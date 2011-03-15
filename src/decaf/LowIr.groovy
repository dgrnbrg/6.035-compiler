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
    if (next instanceof LowIrValueBridge) {
      return new LowIrValueBridge(this.begin, next.end)
    } else {
      return new LowIrBridge(this.begin, next.end)
    }
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
}

class LowIrNode implements GraphNode{
  def predecessors = []
  def successors = []

  static int labelNum = 0
  def label = 'label'+(labelNum++)

  List getPredecessors() { predecessors }
  List getSuccessors() { successors }

  static void link(LowIrNode fst, LowIrNode snd) {
    fst.successors << snd
    snd.predecessors << fst
  }
}

class LowIrCondJump extends LowIrNode {
  TempVar condition
  LowIrNode trueDest, falseDest
}

class LowIrCallOut extends LowIrValueNode {
  String name
  TempVar[] paramTmpVars
}


class LowIrMethodCall extends LowIrValueNode {
  MethodDescriptor descriptor
  TempVar[] paramTmpVars
}

class LowIrReturn extends LowIrValueNode {
  TempVar tmpVar
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
  TempVar leftTmpVar, rightTmpVar
  BinOpType op
}

class LowIrMov extends LowIrNode {
  TempVar src, dst
}
