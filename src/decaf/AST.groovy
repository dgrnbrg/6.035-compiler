package decaf
import antlr.collections.AST as AntlrAST

class AST {
  @Delegate AntlrAST antlrNode
  //root has null as parent
  AST parent
  static def cache = [:]
  def walkerDelegate = new ImplicitWalkerDelegate()

  def eachChild(Closure c) {
    AntlrAST child = antlrNode.getFirstChild()
    while (child) {
      c(fromAntlrAST(child, this))
      child = child.getNextSibling()
    }
  }

  def inOrderWalk(Closure c) {
    walkerDelegate.walk = {->
      eachChild { AST child ->
        child.inOrderWalk(c)
       }
    }
    c.delegate = walkerDelegate
    c(this)
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

  //Override Antlr's AST so that you can do
  //parser.getAST() as AST to get to this class
  static {
    AntlrAST.metaClass.asType = { Class type -> 
      if (type == AST.class) return fromAntlrAST(delegate)
    }
  }
}
