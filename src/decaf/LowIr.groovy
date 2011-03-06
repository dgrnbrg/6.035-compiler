package decaf
import decaf.graph.*

class LowIrStatement{}

class LowIrPath {
  LowIrBlock begin, end

  LowIrPath(begin, end) {
    this.begin = begin
    this.end = end
  }

  static LowIrPath unit(LowIrBlock block) {
    return new LowIrPath(block, block)
  }
}

class LowIrBlock implements GraphNode {
  private static int genvar = 0
  String id
  List<LowIrStatement> statements = []

  LowIrBlock() {
    id = "lowirblock${genvar++}"
  }

  List getSuccessors() {
    def lastInstr = statements[-1]
    switch (lastInstr) {
    case UnconditionalJump:
      return lastInstr.destination != null ? [lastInstr.destination] : []
    case ConditionalJump:
      return [statements[-1].trueDestination, statements[-1].falseDestination]
    default: assert false
    }
  }

  List getPredecessors() {
    return []
  }

  String toString() {
    "$id: $statements"
  }
}

class SSAVar{
  int id
}

class Allocate{
  VariableDescriptor descriptor
}

class Phi{
  List choices
  def result
}

class CallMethod{
  MethodDescriptor descriptor
  List arguments
  def result
}

class Callout{
  StringLiteral name
  List arguments
  def result
}

class LowIrBinOp{
  def left
  def right
  // Ensure that HiIr.groovy is actually imported
  BinOpType op
  def result

  String toString() {
    def opStr = ['+','-','*','/','%','<','>',
     '<=','>=','==','!=','&&','||','!'][BinOpType.findIndexOf{it == op}]
    "LowIrBinOp($opStr, $left, $right, $result)"
  }
}

class LoadConstant{
  int constant
  def result 

  String toString() {
    "LoadConst($constant, $result)"
  }
}

class UnconditionalJump{
  LowIrBlock destination

  String toString() {
    "Jump(${destination?.id})"
  }
}

class ConditionalJump{
  def condition
  LowIrBlock trueDestination
  LowIrBlock falseDestination

  String toString() {
    "CondJump($condition, $trueDestination.id, $falseDestination.id)"
  }
}

class LowIrReturn{
}

// For non-SSA control flow graph
class LoadVariable{
  def result
  VariableDescriptor variable
}

class StoreVariable{
  def valueToStore
  VariableDescriptor destination

  String toString() {
    "StoreVar($valueToStore, $destination)"
  }
}
