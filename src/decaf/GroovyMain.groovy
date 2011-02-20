package decaf
import antlr.*
import groovy.util.*
import org.apache.commons.cli.*
import static decaf.DecafScannerTokenTypes.*

public class GroovyMain {
  static Map typeToName = {
    def tmp = [:]
    DecafParserTokenTypes.getFields().each{ tmp[it.getInt()] = it.name }
    tmp
  }()

  public static void main(String[] args) {
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
      try {
        def lexer = new DecafScanner(new File(file).newDataInputStream())
        def parser = new DecafParser(lexer)
        parser.program()
        def ast = parser.getAST() as AST

        if (argparser['target'] == 'antlrast') {
          println 'digraph g {'
          ast.inOrderWalk { cur ->
            println "${cur.hashCode()} [label=\"${typeToName[cur.getType()]}= ${cur.getText()}\"]"
            walk()
            if (delegate.parent) {
              println "${parent.hashCode()} -> ${cur.hashCode()}"
            }
          }
          println '}'
        }

        if (argparser['target'] == 'hiir') {
          def hb = new HiIrBuilder();
          ast.inOrderWalk(hb.c)
          println 'digraph g {'
          hb.methods.each {k, v ->
            def parentStack = [k]
            println "${k.hashCode()} [label=\"$k\"]"
            v.inOrderWalk { cur ->
              println "${cur.hashCode()} [label=\"$cur\"]"
              parentStack << cur
              walk()
              parentStack.pop()
              println "${parentStack[-1].hashCode()} -> ${cur.hashCode()}"
            }
          }
          println '}'
        }
      } catch (RecognitionException e) {
        e.printStackTrace()
        System.exit(1)
      }
      break
    }
//    println "${tokenLookup}"
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
