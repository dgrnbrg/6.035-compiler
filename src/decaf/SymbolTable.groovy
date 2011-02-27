package decaf

class SymbolTable extends WalkableImpl {
  def children = []
  @Delegate(interfaces=false) AbstractMap map = [:]
  boolean checkCanonical

  void setParent(parent) {
    super.setParent(parent)
    if (parent != null) {
      parent.children << this
    } 
  }

  void howToWalk(Closure c) {
    children.each {
      it.inOrderWalk(c)
    }
  }
  def getAt(String symbol) {
    return map[symbol] ?: parent?.getAt(symbol)
  }

  def putAt(String symbol, desc) {
    if (checkCanonical && !symbol.is(symbol.intern()))
      System.err.println("Warning: adding uninterned symbol $symbol")
    map[symbol] = desc
  }

  String toString() {
    return "SymbolTable(${map.values()})"
  }  
}

enum Type {
  INT, BOOLEAN, INT_ARRAY, BOOLEAN_ARRAY, VOID
}

public class VariableDescriptor {
  String name
  Type type
  def arraySize
  FileInfo fileInfo

  String toString() {
    "$type $name" + (arraySize ? "[$arraySize]" : "")
  }
}

public class MethodDescriptor {
  String name
  Type returnType
  Block block
  List<VariableDescriptor> params = []
  FileInfo fileInfo

  String toString() {
    "$returnType $name($params)"
  }
}
