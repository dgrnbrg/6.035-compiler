package decaf
import decaf.test.HiIrBuilder
import static decaf.BinOpType.*

//handeExpr returns [statementList: List<LowIrStatement>, descriptorContainingResult: VarDesc]
//handleStatement returns LowIrPath (both blocks must end with unconditional jump)

//todo: CallOut, MethodCall, Locations, Return

class LowIrGenerator {
  static int varDescInt = 0
  def genVarDesc() {
    return new VariableDescriptor(name: "genvar${varDescInt++}", type: Type.INT)
  }

  def handleExpr(IntLiteral il) {
    def tmp = genVarDesc()
    return [[new LoadConstant(constant: il.value, result: tmp)], tmp]
  }

  def handleExpr(BooleanLiteral bl) {
    def tmp = genVarDesc()
    return [[new LoadConstant(constant: bl.value ? 1 : 0, result: tmp)], tmp]
  }

  def handleExpr(BinOp binop) {
    def tmp = genVarDesc()
    def left = handleExpr(binop.left)
    def right = handleExpr(binop.right)
    def lowirOp = new LowIrBinOp(op: binop.op, left: left[1], right: right[1], result: tmp)
    def result = left[0]
    result += right[0]
    result << lowirOp
    return [result, tmp]
  }

  def handleExpr(Location loc) {
    if (loc.indexExpr == null) {
      return [[], loc.descriptor]
    } else {
      def index = handleExpr(loc.indexExpr)
      //todo: how to handle array indices?
    }
  }

  LowIrPath makeStmtPath(stmts) {
    stmts << new UnconditionalJump()
    def lowirblock = new LowIrBlock(statements: stmts)
    return LowIrPath.unit(lowirblock)
  }

  LowIrBlock makeNoOpBlock() {
    return new LowIrBlock(statements: [new UnconditionalJump()])
  }

/*
  LowIrPath seq(LowIrBlock first, LowIrPath rest) {
    if (rest.begin.is(rest.end)) {
      first.statements += rest.begin.statements
      return LowIrPath(first, first)
    } else {
      assert first.statements[-1] instanceof UnconditionalJump
      first.statements[-1].destination = rest.begin
      return LowIrPath(first, rest.end)
    }
  }
*/
  LowIrPath seq(LowIrPath s, LowIrPath t) {
    assert s.end.statements[-1] instanceof UnconditionalJump
    s.end.statements[-1].destination = t.begin
    return new LowIrPath(s.begin, t.end)
  }

  LowIrPath handleStatement(Assignment ass) {
    def loc = handleExpr(ass.loc)
    def val = handleExpr(ass.expr)
    def result = loc[0]
    result += val[0]
    result << new StoreVariable(valueToStore: val[1], destination: loc[1])
    return makeStmtPath(result)
  }

  LowIrPath handleStatement(Block block) {
    def result = LowIrPath.unit(makeNoOpBlock())
    block.statements.each {
      switch (it) {
      case Break:
        result.end.statements[-1].destination = forStack[-1][1]
        return result
      case Continue:
        result.end.statements[-1].destination = forStack[-1][0]
        return result
      default:
        result = seq(result, handleStatement(it))
        break
      }
    }
    return result
  }

  LowIrPath handleStatement(IfThenElse ifelse) {
    def cond = handleExpr(ifelse.condition)
    def thenPath = handleStatement(ifelse.thenBlock)
    def elsePath = handleStatement(ifelse.elseBlock)
    def endPath = LowIrPath.unit(makeNoOpBlock())
    def startBlock = new LowIrBlock(statements: cond[0])
    startBlock.statements << new ConditionalJump(
      condition: cond[1], trueDestination: thenPath.begin, falseDestination: elsePath.begin)
    //allow for breaks and continues
    if (thenPath.end.statements[-1].destination == null)
      seq(thenPath, endPath)
    if (elsePath.end.statements[-1].destination == null)
      seq(elsePath, endPath)
    return new LowIrPath(startBlock, endPath.end)
  }

  def forStack = []

  LowIrPath handleStatement(ForLoop forLoop) {
    //get hi and low blocks
    def low = handleExpr(forLoop.low)
    def high = handleExpr(forLoop.high)
    //get the descriptor of the induction var
    def indexDesc = forLoop.index.descriptor
    //initialize the low and high tmp descriptors
    def setupStmts = low[0] + high[0]
    setupStmts << new StoreVariable(valueToStore: low[1], destination: indexDesc)
    def setupPath = makeStmtPath(setupStmts)
    //create the condition test
    def hb = new HiIrBuilder()
    hb.symTable['i'] = indexDesc
    hb.symTable['high'] = high[1]
    def condition = handleExpr(hb.BinOp(LT) {
      Location('i')
      Location('high')
    })
    def incrementer = handleStatement(hb.Assignment {
      Location('i')
      BinOp(ADD) {
        lit(1)
        Location('i')
      }
    })
    //need somewhere to jump to
    def endBlock = makeNoOpBlock()
    //add jump to condition test
    def conditionJump = new ConditionalJump(condition: condition[1], falseDestination: endBlock)
    condition[0] << conditionJump
    def conditionBlock = new LowIrBlock(statements: condition[0])
    //push the increment and end blocks onto the forstack for break and continue
    forStack << [incrementer.begin, endBlock]
    //get the body of the loop
    def bodyPath = handleStatement(forLoop.block)
    forStack.pop()
    //connect the condition's jump to the body
    conditionJump.trueDestination = bodyPath.begin
    //connect init to condition
    setupPath.end.statements[-1].destination = conditionBlock
    //make inc block and connect it between body and condition
    incrementer.end.statements[-1].destination = conditionBlock
    seq(bodyPath, incrementer)
    return new LowIrPath(setupPath.begin, endBlock)
  }
}
