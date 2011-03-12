package decaf

//satisfy groovyc ant task
class HiIr {
}

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
    left.parent = this
    left.inOrderWalk(c)
    if (op != BinOpType.NOT) {
      right.parent = this
      right.inOrderWalk(c)
    }
  }

  public String toString() {
    def opStr = ['+','-','*','/','%','<','>',
     '<=','>=','==','!=','&&','||','!'][BinOpType.findIndexOf{it == op}]
    "BinOp($opStr)"
  }
}

class IntLiteral extends WalkableImpl implements Expr {
  long value

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
  List params = []

  void howToWalk(Closure c) {
    params.each {
      it.parent = this
      it.inOrderWalk(c)
    }
  }

  public String toString() {
    "Callout($name)"
  }
}

class MethodCall extends WalkableImpl implements Expr, Statement {
  MethodDescriptor descriptor
  List<Expr> params = []

  void howToWalk(Closure c) {
    params.each {
      it.parent = this
      it.inOrderWalk(c)
    }
  }
  
  public String toString(){
    "MethodCall($descriptor)"
  }
}

class Block extends WalkableImpl implements Statement {
  def symbolTable
  List<Statement> statements = []

  void howToWalk(Closure c) {
    statements.each { stmt ->
      stmt.parent = this
      stmt.inOrderWalk(c)
    }
  }
  
  public String toString(){
    "Block()"
  }
}

class Location extends WalkableImpl implements Expr {
  VariableDescriptor descriptor
  //if indexExpr is null then this is a scalar variable
  //if it's non-null, it is an array with an index offset
  Expr indexExpr

  void howToWalk(Closure c) {
    indexExpr?.parent = this
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
    loc.parent = this
    loc.inOrderWalk(c)
    expr.parent = this
    expr.inOrderWalk(c)
  }
  
  public String toString(){
    "Assignment(=)"
  }
}

class Return extends WalkableImpl implements Statement {
  Expr expr

  void howToWalk(Closure c) {
    expr?.parent = this
    expr?.inOrderWalk(c)
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
    condition.parent = this
    condition.inOrderWalk(c)
    thenBlock.parent = this
    thenBlock.inOrderWalk(c)
    elseBlock?.parent = this
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
  SymbolTable symbolTable

  void howToWalk(Closure c) {
    //prevent idiocy
    assert index.indexExpr == null
    index.parent = this
    index.inOrderWalk(c)
    low.parent = this
    low.inOrderWalk(c)
    high.parent = this
    high.inOrderWalk(c)
    block.parent = this
    block.inOrderWalk(c)
  }

  public String toString() {
    "ForLoop(i)"
  }
}
