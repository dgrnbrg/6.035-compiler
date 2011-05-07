package decaf.test
import decaf.*
import decaf.optimizations.*

class SCCPTest extends GroovyTestCase {

  void testArithmConstProp() {
    def sccp = new SparseConditionalConstantPropagation()

    def notNode = new LowIrBinOp(op: BinOpType.NOT, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def notInstVal = new InstVal(0)
    sccp.tmpToInstVal[notNode.leftTmpVar] = notInstVal
    def res1 = sccp.sccpEval(notNode)
    assertEquals(1, res1.constVal)

    def addNode = new LowIrBinOp(op: BinOpType.ADD, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def addInstVal1 = new InstVal(5)
    def addInstVal2 = new InstVal(6)
    sccp.tmpToInstVal[addNode.leftTmpVar] = addInstVal1
    sccp.tmpToInstVal[addNode.rightTmpVar] = addInstVal2
    def res2 = sccp.sccpEval(addNode)
    assertEquals(11, res2.constVal)
    
    def subNode = new LowIrBinOp(op: BinOpType.SUB, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def subInstVal1 = new InstVal(5)
    def subInstVal2 = new InstVal(6)
    sccp.tmpToInstVal[subNode.leftTmpVar] = subInstVal1
    sccp.tmpToInstVal[subNode.rightTmpVar] = subInstVal2
    def res3 = sccp.sccpEval(subNode)
    assertEquals(-1, res3.constVal)
    
    def mulNode = new LowIrBinOp(op: BinOpType.MUL, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def mulInstVal1 = new InstVal(5)
    def mulInstVal2 = new InstVal(6)
    sccp.tmpToInstVal[mulNode.leftTmpVar] = mulInstVal1
    sccp.tmpToInstVal[mulNode.rightTmpVar] = mulInstVal2
    def res4 = sccp.sccpEval(mulNode)
    assertEquals(30, res4.constVal)

    def divNode = new LowIrBinOp(op: BinOpType.DIV, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def divInstVal1 = new InstVal(25)
    def divInstVal2 = new InstVal(5)
    sccp.tmpToInstVal[divNode.leftTmpVar] = divInstVal1
    sccp.tmpToInstVal[divNode.rightTmpVar] = divInstVal2
    def res5 = sccp.sccpEval(divNode)
    assertEquals(5, res5.constVal)

    def modNode = new LowIrBinOp(op: BinOpType.MOD, leftTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)), rightTmpVar: new TempVar(desc: new VariableDescriptor(type: Type.INT)))
    def modInstVal1 = new InstVal(5)
    def modInstVal2 = new InstVal(3)
    sccp.tmpToInstVal[modNode.leftTmpVar] = modInstVal1
    sccp.tmpToInstVal[modNode.rightTmpVar] = modInstVal2
    def res6 = sccp.sccpEval(modNode)
    assertEquals(2, res6.constVal)

    def undefTmp = new TempVar()
    def undefInstVal = new InstVal(LatticeType.UNDEF)
    sccp.tmpToInstVal[undefTmp] = undefInstVal

    def overdefTmp = new TempVar()
    def overdefInstVal = new InstVal(LatticeType.OVERDEF)
    sccp.tmpToInstVal[overdefTmp] = overdefInstVal

    def const1Tmp = new TempVar()
    def const1InstVal = new InstVal(4)
    sccp.tmpToInstVal[const1Tmp] = const1InstVal

    def const2Tmp = new TempVar()
    def const2InstVal = new InstVal(2)
    sccp.tmpToInstVal[const2Tmp] = const2InstVal

//cartesian product for a x b
//  def cartProd(a, b, c) {
//    a.inject([]){ acc, elt -> acc += b.collect{ c(elt, it) } }
  }
}

