package decaf.test
import decaf.*
import groovytools.builder.*
import static decaf.DecafParserTokenTypes.*

public class ASTBuilder{

  def build(Closure c) {
    def builder = new NodeBuilder()
    c.delegate = builder
    return convert(c())
  }

  AST convert(Node node) {
    def val = node.value()
    if (val instanceof List) {
      val = val[0]
    }
    def ret = new MockAST(text:node.name(), type:val)
    def n_childs = node.children().size()
    node.children().eachWithIndex { child, index ->
      if (index > 0) {
        ret.children << convert(child)
      }
    }
    return ret
  }
}

class MockAST extends AST {
  String text
  int type
  def children = []

  def eachChild(Closure c) {
    children.each(c)
  }
}
