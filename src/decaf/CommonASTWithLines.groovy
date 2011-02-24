package decaf

import antlr.CommonAST
import antlr.Token
import antlr.collections.AST as AntlrAST

class CommonASTWithLines extends CommonAST implements AntlrAST {
  int line
  int column

  void initialize(Token tok) {
    super.initialize(tok)
    line = tok.getLine()
    column = tok.getColumn()
  }

  String toString() {
    "text=$text id=$type @ $line, $column"
  }
}
