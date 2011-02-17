package decaf

class SymbolTable {
  SymbolTable parent
  @Delegate(interfaces=false) AbstractMap map = [:]
  boolean checkCanonical

  def getAt(String symbol) {
    return map[symbol] ?: parent?.getAt(symbol)
  }

  def putAt(String symbol, desc) {
    if (checkCanonical && !symbol.is(symbol.intern()))
      System.err.println("Warning: adding uninterned symbol $symbol")
    map[symbol] = desc
  }
}
