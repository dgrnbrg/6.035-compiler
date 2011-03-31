package decaf
import decaf.*
import static decaf.graph.Traverser.eachNodeOf

class SparseConditionalConstantPropagation {

  // contains only tmpVars that are either constant or overdefined
  def tmpToInstVal = [:] //keys:tmpVars, value: InstValues
  HashSet visited
  //HashSet<LowIrNode> controlWL
  HashSet ssaWL

  //symbolic execution to simulate arithmetic
  def sccpEval(LowIrNode node) {
    //def valueChanged = false
    def result = null
    switch (node) {
    case LowIrBinOp:
      switch (node.op) {
      case BinOpType.NOT: 
        result = calculate(node, {x -> 
          new InstVal(x == 0 ? 1 : 0) })
        break
      case BinOpType.ADD:
        result = calculate(node, {x, y -> new InstVal(x + y) })
        break
      case BinOpType.SUB: 
        result = calculate(node, {x, y -> new InstVal(x - y) })
        break
      case BinOpType.MUL:
        result = calculate(node, {x, y -> new InstVal(x * y) })
        break
      case BinOpType.DIV:
        result = calculate(node, {x, y -> new InstVal(x / y) })
        break
      case BinOpType.MOD:
        result = calculate(node, {x, y -> new InstVal(x % y) })
        break
      case BinOpType.LT:
        result = calculate(node, {x,y -> new InstVal( x - y < 0 ? 1 : 0) })
        break
      case BinOpType.GT:
        result = calculate(node, {x,y -> new InstVal( x - y > 0 ? 1 : 0) })
        break
      case BinOpType.LTE:
        result = calculate(node, {x,y -> new InstVal( x - y <= 0 ? 1 : 0) })
        break
      case BinOpType.GTE:
        result = calculate(node, {x,y -> new InstVal( x - y >= 0 ? 1 : 0) })
        break
      case BinOpType.EQ:
        result = calculate(node, {x,y -> new InstVal( x - y == 0 ? 1 : 0) })
        break
      case BinOpType.NEQ:
        result = calculate(node, {x,y -> new InstVal( x - y != 0 ? 1 : 0) })
        break
      case BinOpType.AND:
        result = calculate(node, {x,y -> new InstVal( x + y == 2 ? 1 : 0) })
        break
      case BinOpType.OR:
        result = calculate(node, {x,y -> new InstVal( x + y >= 1  ? 1 : 0) })
        break
      }
    break
    case LowIrPhi:
      result = calculate(node, null)
      break
    }
    tmpToInstVal[node] = result
  }

  InstVal calculate(LowIrNode node, Closure resolve) {
    switch (node) {
    case LowIrBinOp:
      if (node.getUses().any { tmpToInstVal[it].latticeVal == LatticeType.UNDEF }) {
        return new InstVal(LatticeType.UNDEF)
      } else if (node.getUses().any {tmpToInstVal[it].latticeVal == LatticeType.OVERDEF }) {
        return new InstVal(LatticeType.OVERDEF)
      } else {
        //all uses are constants
        def args = node.getUses().collect { tmpToInstVal[it].constVal }
        def instVal = resolve(*args)
        return instVal
      }
    case LowIrPhi:
      if (node.getUses().any { tmpToInstVal[it].latticeVal == LatticeType.OVERDEF}) {
      return new InstVal(LatticeType.OVERDEF)
    } else if (node.getUses().every { tmpToInstVal[it].latticeVal == LatticeType.UNDEF}) {
      return new InstVal(LatticeType.UNDEF)
    } else {
      def listConsts = node.getUses().collect{ tmpToInstVal[it] }.findAll { it.latticeVal == LatticeType.CONST }*.constVal
      assert listConsts.size() > 0
      return listConsts.every { it == listConsts[0] } ? new InstVal(listConsts[0]) : InstVal(LatticeType.OVERDEF)
    }
    }
  }
}

class InstVal {
  LatticeType latticeVal
  int constVal

  InstVal() { }

  InstVal(LatticeType latticeVal) {
    this.latticeVal = latticeVal
  }

  InstVal(Number constVal) {
    this.latticeVal = LatticeType.CONST
    this.constVal = constVal as int
  }
}

enum LatticeType {
  UNDEF,     //instruction has no known value
  CONST,      //instruction has constant value
  OVERDEF    //instruction may have multiple value
}
