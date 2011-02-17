import antlr.collections.AST as AntlrAST

class AST {
  @Delegate AntlrAST antlrNode
  def attributes = [:]
  static def cache = [:]

  def getProperty(String name) {
    return attributes[name]
  }

  void setProperty(String name, value) {
    attributes[name] = value
  }

  def eachChild(Closure c) {
    AntlrAST child = antlrNode.getFirstChild()
    while (child) {
      c(new AST(child))
      child = child.getNextSibling()
    }
  }

  //root has null as parent
  def inOrderWalk(Closure c) {
    inOrderWalkPrivate(c, null)
  }

  private def inOrderWalkPrivate(Closure c, parent) {
    c(this, parent)
    parent = this
    eachChild { AST child ->
      child.inOrderWalkPrivate(c, parent)
    }
  }

  //Override the constructor to return values from the cache
  static {
    AST.metaClass.constructor << { AntlrAST ast ->
      AST result = cache[ast]
      //create it if it doesn't exist
      if (result == null) {
        result = new AST(antlrNode: ast)
        cache[ast] = result
      }
      return result
    }
  }
}
