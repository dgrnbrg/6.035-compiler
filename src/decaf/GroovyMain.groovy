package decaf
import antlr.*
import groovy.util.*
import org.apache.commons.cli.*
import static decaf.DecafScannerTokenTypes.*
import antlr.collections.AST as AntlrAST

public class GroovyMain {
  static Closure makeGraph(PrintStream out, root = null) {
    return { cur ->
      out.println("${cur.hashCode()} [label=\"$cur\"]")
      walk()
      if (cur.parent != null)
        out.println("${parent.hashCode()} -> ${cur.hashCode()}")
      else if (root)
        out.println("${root.hashCode()} -> ${cur.hashCode()}")
    }
  }

  public static void main(String[] args) {
    new GroovyMain(args)
  }

  def argparser
  def file
  def exitHooks = []
  def errors = []

  GroovyMain(args) {
    argparser = new ArgParser()
    try {
      argparser.parse(args as List)
    } catch (e) {
      System.err.println("$e")
      System.exit(1)
    }
    if (argparser['other'].size != 1) {
      println 'You must pass exactly one file to be compiled.'
      System.exit(1)
    }
    file = argparser['other'][0]

    int exitCode = 0
    exitHooks << { ->
      errors*.file = file
      errors.each { println it; exitCode = 1 }
    }

    try {
      depends(this."${argparser['target']}")
    } catch (FatalException e) {
      println e
      exitCode = e.code
    } catch (Throwable e) {
      def skipPrefixes = ['org.codehaus','sun.reflect','java.lang.reflect','groovy.lang.Meta']
      def st = e.getStackTrace().findAll { traceElement ->
        !skipPrefixes.any { prefix ->
          traceElement.getClassName().startsWith(prefix)
        }
      }
      println e
      st.each {
        def location = it.getFileName() != null ? "${it.getFileName()}:${it.getLineNumber()}" : 'Unknown'
        println "  at ${it.getClassName()}.${it.getMethodName()}($location)"
      }
    } finally {
      exitHooks.each{ it() }
    }
    System.exit(exitCode)
  }

  def completedTargets = []
  def runningTargets = []
  def depends(target) {
    if (runningTargets.contains(target)) {
      println "There is a cyclic dependency in the targets, and I'll bet you it's your fault..."
      System.exit(22)
    }
    if (!completedTargets.contains(target)) {
      runningTargets << target
      target()
      runningTargets.pop()
      completedTargets << target
    }
  }

  def scan = {->
    def lexer = new LexerIterator(
      lexer: new DecafScanner(new File(file).newDataInputStream()),
      onError: {e, l -> println "$file $e"; l.consume() })

    lexer.each{ token ->
      def typeRename = [(ID): ' IDENTIFIER', (INT_LITERAL): ' INTLITERAL',
        (CHAR_LITERAL): ' CHARLITERAL', (STRING_LITERAL): ' STRINGLITERAL',
        (TK_true): ' BOOLEANLITERAL', (TK_false): ' BOOLEANLITERAL']
      def text = token.text
      if (token.type == CHAR_LITERAL) {
        text = "'$text'"
      } else if (token.type == STRING_LITERAL) {
        text = "\"$text\""
      }
      println "$token.line${typeRename[token.type] ?: ''} $text"
    }
  }

  def ast
  def parse = {->
    try {
      def lexer = new DecafScanner(new File(file).newDataInputStream())
      def parser = new DecafParser(lexer)
      ASTFactory factory = new ASTFactory()
      factory.setASTNodeClass(CommonASTWithLines.class)
      parser.setASTFactory(factory)
      parser.program()
      ast = AST.fromAntlrAST(parser.getAST())
    } catch (RecognitionException e) {
      e.printStackTrace()
      System.exit(1)
    }
  }

  def dotOut
  def setupDot = {->
    def graphFile, extension = 'pdf'
    if (argparser['o']) {
      graphFile = argparser['o']
    if (graphFile.contains('.'))
      extension = graphFile.substring(graphFile.lastIndexOf('.') + 1, graphFile.length())
    } else {
      graphFile = file
      if (graphFile.contains('.'))
        graphFile = graphFile.substring(0, graphFile.lastIndexOf('.'))
      graphFile = graphFile + '.' + argparser['target'] + '.' + extension
      println "Writing output to $graphFile"
    }
    assert graphFile

    def dotCommand = "dot -T$extension -o $graphFile"
    try {
      Process dot = dotCommand.execute()
      dotOut = new PrintStream(dot.outputStream)
      dot.consumeProcessErrorStream(System.err)
      exitHooks << { dotOut.close() }
    } catch (IOException e) {
      println "Dot command: $dotCommand"
      println "Did you install graphviz?"
      e.printStackTrace()
      System.exit(1)
    }
  }

  def antlrast = {->
    depends(parse)
    depends(setupDot)
    dotOut.println('digraph g {')
    ast.inOrderWalk(makeGraph(dotOut))
    dotOut.println('}')
  }

  def symTableGenerator = new SymbolTableGenerator(errors: errors)

  //todo: test that dot is closed even if symtable not generated
  //ie test that the finally block in the entry point is executed when we system.exit
  def genSymTable = {->
    depends(parse)
    ast.inOrderWalk(symTableGenerator.c)
    if (errors != []) throw new FatalException(code: 1)
  }

  def symtable = {->
    depends(genSymTable)
    depends(setupDot)
    dotOut.println('digraph g {')
    ast.symTable.inOrderWalk(makeGraph(dotOut))
    ast.methodSymTable.inOrderWalk(makeGraph(dotOut))
    dotOut.println('}')
  }

  def hiirGenerator = new HiIrGenerator()

  def genHiIr = {->
    depends(genSymTable)
    ast.inOrderWalk(hiirGenerator.c)
  }

  def hiir = {->
    depends(genHiIr)
    depends(setupDot)
    dotOut.println('digraph g {')
    hiirGenerator.methods.each {k, v ->
      dotOut.println("${k.hashCode()} [label=\"$k\"]")
      v.inOrderWalk(makeGraph(dotOut, k))
    }
    dotOut.println '}'
  }

  def inter = {->
    depends(genHiIr)
    def checker = new SemanticChecker(errors: errors, methodSymTable: ast.methodSymTable)
    hiirGenerator.methods.values().each { methodHiIr ->
      assert methodHiIr != null
      //ensure that all HiIr nodes have their fileInfo
      methodHiIr.inOrderWalk{
        assert it.fileInfo != null
        walk()
      }
      checker.checks.each { check ->
        assert check != null
        methodHiIr.inOrderWalk(check)
      }
    }
    if (errors != []) throw new FatalException(code: 1)
  }
}

class LexerIterator {
  def lexer
  Closure onError

  def each(Closure c) {
    boolean done = false
    def token
    while (!done) {
      try {
        for (token = lexer.nextToken(); token.type != EOF; token = lexer.nextToken()) {
          c(token)
        }
        done = true
      } catch (Exception e) {
        onError(e, lexer)
      }
    }
  }
}

class FatalException extends RuntimeException {
  String msg = 'Encountered too many errors, giving up'
  int code = 0
  String toString() {
    return msg
  }
}
