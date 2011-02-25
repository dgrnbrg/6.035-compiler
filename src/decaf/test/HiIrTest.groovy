package decaf.test
import decaf.*

class HiIrTest extends GroovyTestCase {
  void testStatementWalkOrder() {
    def callout = new CallOut(name:"TestCalloutFunction", params:[new IntLiteral(value:2)])
    def ifCondition = new BooleanLiteral(value:true)
    def ifThenBlock = new Block(statements:[new Return(expr:new IntLiteral(value:1))])
    def ifElseBlock = new Block(statements:[callout, new Break()])
    def ifStatement = new IfThenElse(condition:ifCondition, thenBlock:ifThenBlock, elseBlock:ifElseBlock)

    def statementList = []

    ifStatement.inOrderWalk{current ->
      walk()
      if(current instanceof BooleanLiteral){
        assertEquals(true, current.value)
      }
      else if(current instanceof Statement && !(current instanceof Block)){
        statementList << current
      }
    }
    // Our test is meant to confirm the correct order of the tree walk
    // Test: then block gets processed before else block
    // Test: the statements present in a Block are processed from left to right (left being the start of the list)
    assertTrue(statementList[0] instanceof Return)
    assertTrue(statementList[1] instanceof CallOut)
    assertTrue(statementList[2] instanceof Break)
  }

  void testIfThenElse() {
    def ifCondition = new BooleanLiteral(value:true)
    def ifThenBlock = new Block(statements:[new Return(expr:new IntLiteral(value:1))])
    def ifElseBlock = new Block(statements:[new Break()])
    def ifStatement = new IfThenElse(condition:ifCondition, thenBlock:ifThenBlock, elseBlock:ifElseBlock)

    def statementList = []

    ifStatement.inOrderWalk{current ->
      walk()
      if(current instanceof BooleanLiteral){
        assertEquals(true, current.value)
      }
      else if(current instanceof Statement && !(current instanceof Block)){
        statementList << current
      }
    }
    // Our test is meant to confirm the correct order of the tree walk
    // Test: then block gets processed before else block
    assertTrue(statementList[0] instanceof Return)
    assertTrue(statementList[1] instanceof Break)
  }

  void testBreak() {
    def breakStatement = new Break()

    breakStatement.inOrderWalk{ current ->
      walk()
      assertTrue(current instanceof Break)
    }
  }
  void testContinue() {
    def continueStatement = new Continue()

    continueStatement.inOrderWalk{ current ->
      walk()
      assertTrue(current instanceof Continue)
    }
  }
  void testReturn() {
    def returnExpr = new IntLiteral(value:1)
    def testReturn = new Return(expr:returnExpr)
    def treeList = []

    testReturn.inOrderWalk{ current->
      walk()
      treeList << current
    }
    assertTrue(treeList[0] instanceof IntLiteral)
    assertTrue(treeList[1] instanceof Return)
  }
  void testIntLiteral() {
    def i = new IntLiteral(value:9)
    def count = 0
    i.inOrderWalk{ cur ->
      walk()
      assertEquals(9,cur.value)
      count++
    }
    assertEquals(1,count)
  }

  void testBoolLiteral() {
    def i = new BooleanLiteral(value:true)
    def count = 0
    i.inOrderWalk{ cur ->
      walk()
      assertEquals(true,cur.value)
      count++
    }
    assertEquals(1,count)
  }

  void testBinOp() {
    def binOp = new BinOp(op:BinOpType.ADD,
      left: new IntLiteral(value:3), right: new IntLiteral(value:5))
    def l = []
    binOp.inOrderWalk{ l << it; walk() }
    assertEquals(3, l.size)
    assertEquals(binOp, l[0])
    assertEquals(3, l[1].value)
    assertEquals(5, l[2].value)

    binOp = new BinOp(op:BinOpType.NOT,
      left: new BooleanLiteral(value:false))
    l = []
    binOp.inOrderWalk{ l << it; walk() }
    assertEquals(2, l.size)
    assertEquals(binOp, l[0])
    assertEquals(false, l[1].value)

    assertToString(new BinOp(op: BinOpType.ADD), 'BinOp(+)')
    assertToString(new BinOp(op: BinOpType.SUB), 'BinOp(-)')
    assertToString(new BinOp(op: BinOpType.MUL), 'BinOp(*)')
    assertToString(new BinOp(op: BinOpType.DIV), 'BinOp(/)')
    assertToString(new BinOp(op: BinOpType.MOD), 'BinOp(%)')
    assertToString(new BinOp(op: BinOpType.LT), 'BinOp(<)')
    assertToString(new BinOp(op: BinOpType.GT), 'BinOp(>)')
    assertToString(new BinOp(op: BinOpType.LTE), 'BinOp(<=)')
    assertToString(new BinOp(op: BinOpType.GTE), 'BinOp(>=)')
    assertToString(new BinOp(op: BinOpType.EQ), 'BinOp(==)')
    assertToString(new BinOp(op: BinOpType.NEQ), 'BinOp(!=)')
    assertToString(new BinOp(op: BinOpType.AND), 'BinOp(&&)')
    assertToString(new BinOp(op: BinOpType.OR), 'BinOp(||)')
    assertToString(new BinOp(op: BinOpType.NOT), 'BinOp(!)')
  }

  void testCallout() {
    def call = new CallOut(params:[new StringLiteral(value:'hello'), new IntLiteral(value:1),
      new StringLiteral(value:'world'), new BooleanLiteral(value: true)])
    def l = []
    call.inOrderWalk{ l << it; walk() }
    assertEquals(5, l.size())
    assertEquals(call, l[0])
    assertEquals(['hello',1,'world',true], l.subList(1,5).value)
  }

  void testMethodCall() {
    def call = new MethodCall(params:[new StringLiteral(value:'hello'), new IntLiteral(value:1),
      new StringLiteral(value:'world'), new BooleanLiteral(value: true)])
    def l = []
    call.inOrderWalk{ l << it; walk() }
    assertEquals(5, l.size())
    assertEquals(call, l[0])
    assertEquals(['hello',1,'world',true], l.subList(1,5).value)
  }

}
