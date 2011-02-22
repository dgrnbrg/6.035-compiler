package decaf
import antlr.*
import groovy.util.*
import org.apache.commons.cli.*
import static decaf.DecafScannerTokenTypes.*

public class GroovyMain {
  static Closure makeGraph(PrintStream out, root = null) {
    def parentStack = []
    if (root) parentStack << root
    return { cur ->
      out.println("${cur.hashCode()} [label=\"$cur\"]")
      parentStack << cur
      walk()
      parentStack.pop()
      if (parentStack)
        out.println("${parentStack[-1].hashCode()} -> ${cur.hashCode()}")
    }
  }

  public static void main(String[] args) {
    new GroovyMain(args)
  }

  GroovyMain(args) {
    def argparser = new ArgParser()
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
    def file = argparser['other'][0]
    switch (argparser['target']) {
    case 'scan':
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
      break;
    case 'parse':
    case 'antlrast':
    case 'hiir':
    case 'symtable':
      def out
      try {
        def lexer = new DecafScanner(new File(file).newDataInputStream())
        def parser = new DecafParser(lexer)
        parser.program()
        def ast = parser.getAST() as AST

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
          out = new PrintStream(dot.outputStream)
          dot.consumeProcessErrorStream(System.err)

          if (argparser['target'] == 'antlrast') {
            out.println('digraph g {')
            ast.inOrderWalk(makeGraph(out))
            out.println('}')
          }

          if (argparser['target'] == 'symtable') {
            def sb = new SymbolTableGenerator()
            ast.inOrderWalk(sb.c)
            out.println('digraph g {')
            ast.symTable.inOrderWalk(makeGraph(out))
            ast.methodSymTable.inOrderWalk(makeGraph(out))
            out.println('}')
          }

          if (argparser['target'] == 'hiir') {
            def sb = new SymbolTableGenerator()
            ast.inOrderWalk(sb.c)
            def hb = new HiIrBuilder();
            ast.inOrderWalk(hb.c)
            out.println('digraph g {')
            hb.methods.each {k, v ->
              out.println("${k.hashCode()} [label=\"$k\"]")
              v.inOrderWalk(makeGraph(out, k))
            }
            out.println '}'
          }
        } catch (IOException e) {
          println "Dot command: $dotCommand"
          println "Did you install graphviz?"
          e.printStackTrace()
          System.exit(1)
        }
      } catch (RecognitionException e) {
        e.printStackTrace()
        System.exit(1)
      } finally {
        out?.close()
      }
      break
    }
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
