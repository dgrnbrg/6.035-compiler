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
  TempVar tmpVar

  LowIrValueBridge(LowIrValueNode node) {
    super(node)
    tmpVar = node.tmpVar
  }

  LowIrValueBridge(LowIrNode begin, LowIrValueNode end) {
    super(begin, end)
    tmpVar = end.tmpVar
  }
}

class LowIrNode implements GraphNode{
  def predecessors = []
  def successors = []

  def metaText = ''
  def frak = false

  static int labelNum = 0
  def label = 'label'+(labelNum++)

  List getPredecessors() { predecessors }
  List getSuccessors() { successors }

  static void link(LowIrNode fst, LowIrNode snd) {
    fst.successors << snd
    snd.predecessors << fst
  }

  String toString() {
    "LowIrNode($metaText)"
  }
}

class LowIrCondJump extends LowIrNode {
  TempVar condition
  LowIrNode trueDest, falseDest

  String toString() {
    "LowIrCondJump(condition: $condition)"
  }
}

class LowIrCallOut extends LowIrValueNode {
  String name
  TempVar[] paramTmpVars

  String toString() {
    "LowIrCallOut(method: $name, tmpVar: $tmpVar, params: $paramTmpVars)"
  }
}


class LowIrMethodCall extends LowIrValueNode {
  MethodDescriptor descriptor
  TempVar[] paramTmpVars

  String toString() {
    "LowIrMethodCall(method: $descriptor.name, tmpVar: $tmpVar, params: $paramTmpVars)"
  }
}

class LowIrReturn extends LowIrValueNode {
  TempVar tmpVar

  String toString() {
    "LowIrReturn(tmpVar: $tmpVar)"
  }
}

class LowIrValueNode extends LowIrNode{
  TempVar tmpVar

  String toString() {
    "LowIrValueNode($metaText, tmpVar: $tmpVar)"
  }
}

class LowIrStringLiteral extends LowIrValueNode {
  String value

  String toString() {
    "LowIrStringLiteral(value: $value, tmpVar: $tmpVar)"
  }
}

class LowIrIntLiteral extends LowIrValueNode {
  int value

  String toString() {
    "LowIrIntLiteral(value: $value, tmpVar: $tmpVar)"
  }
}

class LowIrBinOp extends LowIrValueNode {
  TempVar leftTmpVar, rightTmpVar
  BinOpType op

  String toString() {
    "LowIrBinOp(op: $op, leftTmp: $leftTmpVar, rightTmp: $rightTmpVar, tmpVar: $tmpVar)"
  }
}

class LowIrMov extends LowIrNode {
  TempVar src, dst

  String toString() {
    "LowIrMov(src: $src, dst: $dst)"
  }
}

class LowIrStore extends LowIrNode {
  VariableDescriptor desc
  TempVar index
  TempVar value //this is what gets stored

  String toString() {
    "LowIrStore(dest: $desc, index: $index)"
  }
}

class LowIrLoad extends LowIrValueNode {
  VariableDescriptor desc
  TempVar index

  String toString() {
    "LowIrLoad(dest: $desc, index: $index)"
  }
}
