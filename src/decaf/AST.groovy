package decaf
import antlr.collections.AST as AntlrAST

class AST extends WalkableImpl {
  static Map typeToName = {
    def tmp = [:]
    DecafParserTokenTypes.getFields().each{ tmp[it.getInt()] = it.name }
    tmp
  }()

  @Delegate AntlrAST antlrNode
  //root has null as parent
  AST parent
  static def cache = [:]

  def eachChild(Closure c) {
    AntlrAST child = antlrNode.getFirstChild()
    while (child) {
      c(fromAntlrAST(child, this))
      child = child.getNextSibling()
    }
  }

  void howToWalk(Closure c) {
    eachChild { AST child ->
      child.inOrderWalk(c)
    }
  }

  static def fromAntlrAST(AntlrAST ast, AST parent = null) {
      AST result = cache[ast]
      //create it if it doesn't exist
      if (result == null) {
        result = new AST(antlrNode: ast, parent: parent)
        result.walkerDelegate.declVar('parent', parent)
        cache[ast] = result
      }
      return result
  }

  String toString() {
    "AST(${typeToName[getType()]}, \\\"${getText()}\\\")"
  }

  //Override Antlr's AST so that you can do
  //parser.getAST() as AST to get to this class
  static {
    AntlrAST.metaClass.asType = { Class type -> 
      if (type == AST.class) return fromAntlrAST(delegate)
    }
  }
}
