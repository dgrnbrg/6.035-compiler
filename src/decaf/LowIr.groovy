package decaf
import decaf.graph.*

class LowIr {}

class LowIrBridge {
  LowIrNode begin, end

  LowIrBridge(LowIrNode node) {
    begin = node
    end = node
  }

  LowIrBridge(LowIrNode begin, LowIrNode end) {
    this.begin = begin
    this.end = end
  }

  LowIrBridge seq(LowIrBridge next) {
    LowIrNode.link(this.end, next.begin)
    return new LowIrBridge(this.begin, next.end)
  }
}

class LowIrValueBridge extends LowIrBridge {
  // int tmpNum
  TempVar tmpVar

  LowIrValueBridge(LowIrValueNode node) {
    super(node)
    // tmpNum = node.tmpNum
    tmpVar = node.tmpVar
  }

  LowIrValueBridge(LowIrNode begin, LowIrValueNode end) {
    super(begin, end)
    // tmpNum = end.tmpNum
    tmpVar = end.tmpVar
  }

  LowIrBridge seq(LowIrBridge next) {
    LowIrNode.link(this.end, next.begin)
    return new LowIrValueBridge(this.begin, next.end)
  }
}

class LowIrNode implements GraphNode{
  def predecessors = []
  def successors = []

  List getPredecessors() { predecessors }
  List getSuccessors() { successors }

  static void link(LowIrNode fst, LowIrNode snd) {
    fst.successors << snd
    snd.predecessors << fst
  }
}

class LowIrCallOut extends LowIrNode {
  String name
  // int[] paramNums
  TempVar[] paramTmpVars
}


class LowIrMethodCall extends LowIrNode {
  MethodDescriptor descriptor
  TempVar[] tempVars //TODO: talk to Nathan -- change accordingly
}

class LowIrValueNode extends LowIrNode{
  TempVar tmpVar
}

class LowIrStringLiteral extends LowIrValueNode {
  String value
}

class LowIrIntLiteral extends LowIrValueNode {
  int value
}

class LowIrBinOp extends LowIrValueNode {
  //int leftTmpNum, rightTmpNum
  TempVar leftTmpVar, rightTmpVar
  BinOpType op
}
