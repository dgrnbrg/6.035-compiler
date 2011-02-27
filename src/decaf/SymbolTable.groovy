package decaf

class SymbolTable extends WalkableImpl {
  def children = []
  @Delegate(interfaces=false) AbstractMap map = [:]
  boolean checkCanonical

  SymbolTable(SymbolTable parent) {
    setParent(parent)
  }

  //Note that typing parent like this is really important to make sure all the
  //code that should be run is run
  void setParent(WalkableImpl parent) {
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

  boolean equals(Object other) {
    if (other instanceof SymbolTable) {
      if (other.children == children) {
        return true
      }
    }
  return false
  }

  int hashCode() {
    return children.hashCode() * 19 + parent.hashCode() * 41
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

  boolean equals(Object other) {
    if (other instanceof VariableDescriptor) {
      if (other.name == name &&
        type == other.type &&
        arraySize == other.arraySize) {
          return true
      } 
    }
    return false
  }

  int hashCode() {
    def p = arraySize?.hashCode() ?: 0
    return name.hashCode() * 17 + type.hashCode() * 31 + p * 37;
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

  boolean equals(Object other) {
    if (other instanceof MethodDescriptor) {
      if (other.name == name &&
        returnType == other.returnType &&
        params == other.params) {
          return true
      } 
    }
    return false
  }

  int hashCode() {
    return name.hashCode() * 17 + returnType.hashCode() * 31 + params.hashCode() * 37;
  }
}
