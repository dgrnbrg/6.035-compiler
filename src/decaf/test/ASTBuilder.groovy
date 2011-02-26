package decaf.test
import decaf.*
import groovytools.builder.*
import static decaf.DecafParserTokenTypes.*

public class ASTBuilder{

  SymbolTableGenerator symTableGen = new SymbolTableGenerator()

  def compile(Closure c) {
    def errors = []
    symTableGen.errors = errors
    AST ast = build(c)
    ast.inOrderWalk(symTableGen.c)
    if (errors != []) return errors
    def hiir = new HiIrBuilder()
    ast.inOrderWalk(hiir.c)
    if (errors != []) {
      return errors
    } else {
      return hiir
    }
  }

  def build(Closure c) {
    def builder = new NodeBuilder()
    c.delegate = builder
    return convert(c())
  }

  AST convert(Node node, parent = null) {
    def val = node.value()
    if (val instanceof List) {
      val = val[0]
    }
    def ret = new MockAST(text:node.name(), type:val, parent: parent)
    ret.walkerDelegate.declVar('parent',parent)
    def n_childs = node.children().size()
    node.children().eachWithIndex { child, index ->
      if (index > 0) {
        ret.kids << convert(child, ret)
      }
    }
    return ret
  }
}

class MockAST extends AST {
  String text
  int type
  def kids = []

  def eachChild(Closure c) {
    kids.each(c)
  }
}
