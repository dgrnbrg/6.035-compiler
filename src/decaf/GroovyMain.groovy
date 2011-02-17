package decaf
import antlr.*
import groovy.util.*
import org.apache.commons.cli.*
import static decaf.DecafScannerTokenTypes.*

public class GroovyMain {
  static Map tokenLookup = {
    def tmp = [:]
    DecafParserTokenTypes.getFields().each{ tmp[it.getInt()] = it.name }
    tmp
  }()

  public static void main(String[] args) {
    def argparser = new ArgParser()
    try {
      argparser.parse(args as List)
    } catch (e) {
      println "$e"
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
      try {
        def lexer = new DecafScanner(new File(file).newDataInputStream())
        def parser = new DecafParser(lexer)
        parser.program()
        def ast = new AST(parser.getAST())

        println 'digraph g {'
        ast.inOrderWalk { cur, parent ->
          println "${cur.hashCode()} [label=\"${cur.getText()}\"]"
          if (parent) {
            println "${parent.hashCode()} -> ${cur.hashCode()}"
          }
        }
        println '}'

      } catch (RecognitionException e) {
        e.printStackTrace()
        System.exit(1)
      }
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
