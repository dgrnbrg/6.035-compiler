package decaf

interface Expr extends Walkable {
}

interface Statement extends Walkable {
}

enum BinOpType {
  ADD, SUB, MUL, DIV, MOD,
  LT, GT, LTE, GTE,
  EQ, NEQ,
  AND, OR, NOT
}

class BinOp extends WalkableImpl implements Expr {
  BinOpType op
  Expr left
  Expr right

  void howToWalk(Closure c) {
    left.inOrderWalk(c)
    if (op != BinOpType.NOT)
      right.inOrderWalk(c)
  }

  public String toString() {
    def opStr = ['+','-','*','/','%','<','>',
     '<=','>=','==','!=','&&','||','!'][BinOpType.findIndexOf{it == op}]
    "BinOp($opStr)"
  }
}

class IntLiteral extends WalkableImpl implements Expr {
  int value

  void howToWalk(Closure c) { }
  
  public String toString(){
    "IntLiteral($value)"
  }
}

class BooleanLiteral extends WalkableImpl implements Expr {
  boolean value

  void howToWalk(Closure c) { }
  
  public String toString(){
    "BooleanLiteral($value)"
  }
}

class StringLiteral extends WalkableImpl {
  String value

  void howToWalk(Closure c) {}

  String toString() {
    "StringLiteral($value)"
  }
}

class CallOut extends WalkableImpl implements Expr, Statement {
  def name
  List params

  void howToWalk(Closure c) {
    params.each {
      it.inOrderWalk(c)
    }
  }

  public String toString() {
    "Callout($name)"
  }
}

class MethodCall extends WalkableImpl implements Expr, Statement {
  def descriptor
  List<Expr> params

  void howToWalk(Closure c) {
    params.each {
      it.inOrderWalk(c)
    }
  }
  
  public String toString(){
    "MethodCall($descriptor)"
  }
}

class Block extends WalkableImpl implements Statement {
  def symbolTable
  List<Statement> statements

  void howToWalk(Closure c) {
    statements.each { stmt ->
      stmt.inOrderWalk(c)
    }
  }
  
  public String toString(){
    "Block()"
  }
}

class Location extends WalkableImpl implements Expr {
  def descriptor
  //if indexExpr is null then this is a scalar variable
  //if it's non-null, it is an array with an index offset
  Expr indexExpr

  void howToWalk(Closure c) {
    indexExpr?.inOrderWalk(c)
  }
  
  public String toString(){
    "Location($descriptor)"
  }
}

class Assignment extends WalkableImpl implements Statement {
  Location loc
  Expr expr

  void howToWalk(Closure c) {
    loc.inOrderWalk(c)
    expr.inOrderWalk(c)
  }
  
  public String toString(){
    "Assignment(=)"
  }
}

class Return extends WalkableImpl implements Statement {
  Expr expr

  void howToWalk(Closure c) {
    expr.inOrderWalk(c)
  }

  public String toString(){
    "Return()"
  }
}

class Break extends WalkableImpl implements Statement {

  void howToWalk(Closure c) {}
  
  public String toString(){
    "Break()"
  }
}

class Continue extends WalkableImpl implements Statement {

  void howToWalk(Closure c) {}

  public String toString(){
    "Continue()"
  }
}

class IfThenElse extends WalkableImpl implements Statement {
  Expr condition
  Block thenBlock
  Block elseBlock

  void howToWalk(Closure c) {
    condition.inOrderWalk(c)
    thenBlock.inOrderWalk(c)
    elseBlock?.inOrderWalk(c)
  }

  public String toString() {
    "If()"
  }
}

class ForLoop extends WalkableImpl implements Statement {
  Location index
  Expr low
  Expr high
  Block block

  void howToWalk(Closure c) {
    //prevent idiocy
    assert index.indexExpr == null
    index.inOrderWalk(c)
    low.inOrderWalk(c)
    high.inOrderWalk(c)
    block.inOrderWalk(c)
  }

  public String toString() {
    "ForLoop(i)"
  }
}
