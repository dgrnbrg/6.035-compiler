package decaf
import decaf.*
import static decaf.graph.Traverser.eachNodeOf

class SparseConditionalConstantPropagation {

  // contains only tmpVars that are either constant or overdefined
  def tmpToInstVal = [:] //keys:tmpVars, value: InstValues

  //symbolic execution to simulate arithmetic
  def sccpEval(LowIrNode node) {
    //def valueChanged = false
    switch (node) {
    case LowIrBinOp:
      //def result = latticeCompute(node.getUses(), {list -> list[0] / list[1]})
      if (node.op == BinOpType.NOT) {
        def result = calculate(node, {x -> new InstVal(tmpToInstVal[node.leftTmpVar].constVal == 0 ? 1 : 0) })
        tmpToInstVal[node] = result
        println " the result.constVal is $result.constVal "
      }

    }
  }

  InstVal calculate(LowIrNode node, Closure resolve) {
    if (node.getUses().any { tmpToInstVal[it].latticeVal == LatticeType.UNDEF }) {
      return new InstVal(latticeVal: LatticeType.UNDEF)
    } else if (node.getUses().any {tmpToInstVal[it].latticeVal == LatticeType.OVERDEF }) {
      return new InstVal(latticeVal: LatticeType.OVERDEF)
    } else {
      //all uses are constants
      def instVal = resolve.call(node.getUses().collect { tmpToInstVal[it].constVal })
      return instVal
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

  InstVal(int constVal) {
    this.latticeVal = LatticeType.CONST
    this.constVal = constVal
  }
}

enum LatticeType {
  UNDEF,     //instruction has no known value
  CONST,      //instruction has constant value
  OVERDEF    //instruction may have multiple value
}
