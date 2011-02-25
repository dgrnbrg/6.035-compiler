package decaf.test
import decaf.*

class SemanticCheckTest extends GroovyTestCase {
  // void testGetTypeIntLiteral(){
  //   def intLiteral = new IntLiteral(value:1)
  //   assertEquals(Type.INT, SemanticChecker.getExprType(intLiteral))
  // }
  // void testGetTypeBooleanLiteral(){
  //   def booleanLiteral = new BooleanLiteral(value:true)
  //   assertEquals(Type.BOOLEAN, SemanticChecker.getExprType(booleanLiteral))
  // }
  void testGetTypeBinOpADD(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.ADD, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.INT, binOp.operandType)
  }
  
  void testGetTypeBinOpSUB(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.SUB, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.INT, binOp.operandType)
  }
  
  void testGetTypeBinOpMUL(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.MUL, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.INT, binOp.operandType)
  }
  
  void testGetTypeBinOpDIV(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.DIV, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.INT, binOp.operandType)
  }

  void testGetTypeBinOpMOD(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.MOD, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.INT, binOp.operandType)
  }
  
  void testGetTypeBinOpGT(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.GT, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.BOOLEAN, binOp.operandType)
  }
  
  void testGetTypeBinOpLT(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.LT, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.BOOLEAN, binOp.operandType)
  }
  
  void testGetTypeBinOpGTE(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.GTE, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.BOOLEAN, binOp.operandType)
  }

  void testGetTypeBinOpLTE(){
    def semanticChecker = new SemanticChecker()
    def lhs = new IntLiteral(value:1)
    def rhs = new IntLiteral(value:2)

    def binOp = new BinOp(op:BinOpType.LTE, left:lhs, right:rhs)

    binOp.inOrderWalk(semanticChecker.getExprType)
    assertEquals(Type.BOOLEAN, binOp.operandType)
  }

}
