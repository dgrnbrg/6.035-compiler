package decaf.test
import decaf.*

class SCCPTest extends GroovyTestCase {

  void testArithmConstProp() {
    def sccp = new SparseConditionalConstantPropagation()

    def notNode = new LowIrBinOp(op: BinOpType.NOT, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def notInstVal = new InstVal(0)
    sccp.tmpToInstVal[notNode.leftTmpVar] = notInstVal
    sccp.sccpEval(notNode)
    assertEquals(1, sccp.tmpToInstVal[notNode].constVal)

    def addNode = new LowIrBinOp(op: BinOpType.ADD, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def addInstVal1 = new InstVal(5)
    def addInstVal2 = new InstVal(6)
    sccp.tmpToInstVal[addNode.leftTmpVar] = addInstVal1
    sccp.tmpToInstVal[addNode.rightTmpVar] = addInstVal2
    sccp.sccpEval(addNode)
    assertEquals(11, sccp.tmpToInstVal[addNode].constVal)
    
    def subNode = new LowIrBinOp(op: BinOpType.SUB, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def subInstVal1 = new InstVal(5)
    def subInstVal2 = new InstVal(6)
    sccp.tmpToInstVal[subNode.leftTmpVar] = subInstVal1
    sccp.tmpToInstVal[subNode.rightTmpVar] = subInstVal2
    sccp.sccpEval(subNode)
    assertEquals(-1, sccp.tmpToInstVal[subNode].constVal)
    
    def mulNode = new LowIrBinOp(op: BinOpType.MUL, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def mulInstVal1 = new InstVal(5)
    def mulInstVal2 = new InstVal(6)
    sccp.tmpToInstVal[mulNode.leftTmpVar] = mulInstVal1
    sccp.tmpToInstVal[mulNode.rightTmpVar] = mulInstVal2
    sccp.sccpEval(mulNode)
    assertEquals(30, sccp.tmpToInstVal[mulNode].constVal)

    def divNode = new LowIrBinOp(op: BinOpType.DIV, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def divInstVal1 = new InstVal(25)
    def divInstVal2 = new InstVal(5)
    sccp.tmpToInstVal[divNode.leftTmpVar] = divInstVal1
    sccp.tmpToInstVal[divNode.rightTmpVar] = divInstVal2
    sccp.sccpEval(divNode)
    assertEquals(5, sccp.tmpToInstVal[divNode].constVal)

    def modNode = new LowIrBinOp(op: BinOpType.MOD, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def modInstVal1 = new InstVal(5)
    def modInstVal2 = new InstVal(3)
    sccp.tmpToInstVal[modNode.leftTmpVar] = modInstVal1
    sccp.tmpToInstVal[modNode.rightTmpVar] = modInstVal2
    sccp.sccpEval(modNode)
    assertEquals(2, sccp.tmpToInstVal[modNode].constVal)

    def args1 = []
    args1.add(new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def phiNode1 = new LowIrPhi(args: args1)
    def argInstVal = new InstVal(5)
    sccp.tmpToInstVal[phiNode1.args[0]] = argInstVal
    sccp.sccpEval(phiNode1)
    assertEquals(5, sccp.tmpToInstVal[phiNode1].constVal)

    //TODO: write test cases using the cartesian product
    //cartProd([notNode, addNode, subNode, mulNode, divNode, modNode], [
  }
//cartesian product for a x b
  def cartProd(a, b, c) {
    a.inject([]){ acc, elt -> acc += b.collect{ c(elt, it) } }
  }
}

