package decaf.test
import decaf.*

class HiIrTest extends GroovyTestCase {
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
