package decaf
import antlr.RecognitionException
import antlr.Token
import antlr.collections.AST
import groovy.util.*
import org.apache.commons.cli.*

public class GroovyMain {
  static Map tokenLookup = {
    def tmp = [:]
    DecafParserTokenTypes.getFields().each{ tmp[it.getInt()] = it.name }
    tmp
  }()

  public static void main(String[] args) {
    def lexer = new DecafScanner(new File(args[0]).newDataInputStream());
    def parser = new DecafParser(lexer);
    parser.program();
    println 'digraph g {'
    graphviz(null, parser.getAST())
    println '}'
//    println "${tokenLookup}"
  }
  static def graphviz(parent, node) {
    if (node && node.getText() != null && node.getText() != 'null') {
      println "${node.hashCode()} [label=\"${node.getText()}\"]"
      println "${parent ? parent.hashCode() : 'root'} -> ${node.hashCode()}"
      graphviz(parent, node.getNextSibling())
      if (node.getNumberOfChildren() != 0) {
        graphviz(node, node.getFirstChild())
      }
    }
  }
}

enum Type {
  int32, bool, void_t
}

class Program {
  ArrayDecl[] arrays = []
  VarDecl[] fields = []
  MethodDecl[] methods = []
}

class VarDecl {
  Type type
  String name
}

class ArrayDecl extends VarDecl {
  int size
}

class MethodDecl {
  Type returnType
  String name
  VarDecl[] args = []
  Block body
}

class Block {
  Statement[] statements
}

class Statement {
  
}

