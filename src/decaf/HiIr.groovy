package decaf

interface Expr {
  void inOrderWalk(Closure c);
}

interface Statement {
  void inOrderWalk(Closure c);
}

enum BinOpType {
  ADD, SUB, MUL, DIV, MOD,
  LT, GT, LTE, GTE,
  EQ, NEQ,
  AND, OR, NOT
}

class BinOp implements Expr {
  BinOpType op
  Expr left
  Expr right
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = { ->
      left.inOrderWalk(c.clone())
      if (op != BinOpType.NOT)
        right.inOrderWalk(c.clone())
    }
    c.delegate = iwd
    c(this)
  }

  public String toString() {
    def opStr = ['+','-','*','/','%','<','>',
     '<=','>=','==','!=','&&','||','!'][BinOpType.findIndexOf{it == op}]
    "BinOp($opStr)"
  }
}

class IntLiteral implements Expr {
  int value
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->}
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "IntLiteral($value)"
  }
}

class BooleanLiteral implements Expr {
  boolean value
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->}
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "BooleanLiteral($value)"
  }
}

class CallOut implements Expr, Statement {
  def name
  List params
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->
      params.each {
        it.inOrderWalk(c.clone())
      }
    }
    c.delegate = iwd
    c(this)
  }

  public String toString() {
    "Callout($name)"
  }
}

class MethodCall implements Expr, Statement {
  def descriptor
  List<Expr> params
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->
      params.each {
        it.inOrderWalk(c.clone())
      }
    }
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "MethodCall($descriptor)"
  }
}

class Block implements Statement {
  def symbolTable
  List<Statement> statements
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->
      statements.each { stmt ->
        stmt.inOrderWalk(c.clone())
      }
    }
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "Block()"
  }
}

class Location implements Expr {
  def descriptor
  //if indexExpr is null then this is a scalar variable
  //if it's non-null, it is an array with an index offset
  Expr indexExpr
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->
      indexExpr?.inOrderWalk(c.clone())
    }
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "Location($descriptor)"
  }
}

class Assignment implements Statement {
  Location loc
  Expr expr
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->
      loc.inOrderWalk(c.clone())
      expr.inOrderWalk(c.clone())
    }
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "Assignment(=)"
  }
}

class Return implements Statement {
  Expr expr
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->
      expr.inOrderWalk(c.clone())
    }
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "Return()"
  }
}

class Break implements Statement {
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->}
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "Break()"
  }
}

class Continue implements Statement {
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->}
    c.delegate = iwd
    c(this)
  }
  
  public String toString(){
    "Continue()"
  }
}

class IfThenElse implements Statement {
  Expr condition
  Block thenBlock
  Block elseBlock
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->
      condition.inOrderWalk(c.clone())
      thenBlock.inOrderWalk(c.clone())
      elseBlock?.inOrderWalk(c.clone())
    }
    c.delegate = iwd
    c(this)
  }

  public String toString() {
    "If()"
  }
}

class ForLoop implements Statement {
  Location index
  Expr low
  Expr high
  Block block
  ImplicitWalkerDelegate iwd = new ImplicitWalkerDelegate()

  void inOrderWalk(Closure c) {
    iwd.walk = {->
      //prevent idiocy
      assert index.indexExpr == null
      index.inOrderWalk(c.clone())
      low.inOrderWalk(c.clone())
      high.inOrderWalk(c.clone())
      block.inOrderWalk(c.clone())
    }
    c.delegate = iwd
    c(this)
  }

  public String toString() {
    "ForLoop(i)"
  }
}
