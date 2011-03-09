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
  def value

  LowIrValueBridge(LowIrNode node) {
    super(node)
  }

  LowIrValueBridge(LowIrNode begin, LowIrNode end) {
    super(begin, end)
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
  //must be lowir values
  def params = []
}

class LowIrValueNode extends LowIrNode{
  int tmpNum
}

class LowIrStringLiteral extends LowIrValueNode {
  String value
}

class LowIrIntLiteral extends LowIrValueNode {
  int value
}
