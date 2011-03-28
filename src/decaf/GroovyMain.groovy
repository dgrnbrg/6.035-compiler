package decaf
import antlr.*
import groovy.util.*
import org.apache.commons.cli.*
import decaf.graph.*
import static decaf.DecafScannerTokenTypes.*
import antlr.collections.AST as AntlrAST
import decaf.test.HiIrBuilder

class LowIrDotTraverser extends Traverser {
  def out

  void visitNode(GraphNode cur) {
    // set nodeColor to "" if you don't want to render colors
    def nodeColor = ", style=filled, color=\"${TraceGraph.getColor(cur)}\""
    out.println("${cur.hashCode()} [label=\"$cur Label=${cur.label} \\nTrc = ${cur.anno}\"$nodeColor]")
  }
  void link(GraphNode src, GraphNode dst) {
    out.println("${src.hashCode()} -> ${dst.hashCode()}")
  }
}

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
  def inputStream
  def exitHooks = []
  def errors = []
  //used for storing a failure exception when called from code
  def failException

  static GroovyMain runMain(String target, String decafProgram) {
    def bytes = decafProgram.getBytes()
    def is = new ByteArrayInputStream(bytes)
    def main = new GroovyMain(is)
    try {
      main."$target"()
    } catch (Exception e) {
      main.failException = e
    }
    return main
  }

  GroovyMain(InputStream is) {
    inputStream = is
  }

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
    inputStream = new File(file).newDataInputStream()

    // Here we decide whether to enable the Assert Function
    if(argparser['assertEnabled'] == 'true') {
      AssertFn.AssertFunctionEnabled = true
      //println('assert function enabled.')
    } else {
      //println('assert function not enabled.')
    }

    int exitCode = 0
    exitHooks << { ->
      errors*.file = file
      errors.each { println it; exitCode = 1 }
    }

    try {
      def target = argparser['target']
      if (target == null) target = 'codegen'
      depends(this."$target")
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
      exitCode = 1
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
      lexer: new DecafScanner(inputStream),
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
      def lexer = new DecafScanner(inputStream)
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

  def hiirGenerator = new HiIrGenerator(errors: errors)
  def methodDescs

  def genHiIr = {->
    depends(genSymTable)

    if(AssertFn.AssertFunctionEnabled) {
      ast.methodSymTable["assert"] = AssertFn.getAssertMethodDesc()
    }

    ast.inOrderWalk(hiirGenerator.c)
    methodDescs = ast.methodSymTable.values()
    if (errors != []) throw new FatalException(code: 1)
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
    methodDescs.each { MethodDescriptor desc ->
      def methodHiIr = desc.block
      assert methodHiIr != null
      //ensure that all HiIr nodes have their fileInfo
      methodHiIr.inOrderWalk{
        assert it.fileInfo != null
        walk()
      }
/*
      checker.checks.each { check ->
        assert check != null
        methodHiIr.inOrderWalk(check)
      }
*/
      methodHiIr.inOrderWalk(checker.hyperblast)
    }
    
    checker.mainMethodCorrect()
    
    if (errors != []) throw new FatalException(code: 1)
  }

  def lowir = {->
    depends(setupDot)
    depends(genLowIr)
    def lidt = new LowIrDotTraverser(out: dotOut)
    dotOut.println('digraph g {')
    methodDescs.each { methodDesc ->
      lidt.traverse(methodDesc.lowir)
    }
    dotOut.println '}'
  }

  def lowirGen = new LowIrGenerator()

  def genLowIr = {->
    depends(inter)
    depends(genTmpVars)
    methodDescs.each { MethodDescriptor methodDesc ->
      methodDesc.lowir = lowirGen.destruct(methodDesc).begin 
      // Calculate traces for each method
      TraceGraph.calculateTraces(methodDesc.lowir);
    }

    // Run dataflow analysis (for testing)
    def reaching = new ReachingDefinitions()
    def dataflow = new DataFlowAnalysis<Map<TempVar,TreeSet<Definition>>>(reaching)
    methodDescs.each { MethodDescriptor methodDesc ->
       dataflow.run(methodDesc.lowir)
    }
  }

  def codeGen = new CodeGenerator()

  def genTmpVars = {->
    depends(inter)
    //locals, temps, and params
    methodDescs.each { MethodDescriptor methodDesc ->
      methodDesc.tempFactory.decorateMethodDesc()
    }
    //make space for globals
    ast.symTable.@map.each { name, desc ->
      name += '_globalvar'
      def s = desc.arraySize
      if (s == null) s = 1
      codeGen.emit('bss', ".comm $name ${8*s}")
    }
  }

  def genCode = {->
    depends(genLowIr)
    methodDescs.each { methodDesc ->
      codeGen.handleMethod(methodDesc)
    }
  }

  def codegen = {->
    depends(genCode)
    def file = argparser['o'] ?: this.file + '.s'
    new File(file).text = codeGen.getAsm()
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
