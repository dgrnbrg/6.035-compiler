package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class DeadCodeElimination extends Analizer {
  DeadCodeElimination() {
    dir = AnalysisDirection.BACKWARD
  }

  final void lazyInit(node) {
    if (node.anno['deadcode-liveness'] == null)
      node.anno['deadcode-liveness'] = new HashSet()
  }

  void store(GraphNode node, Set<GraphNode> data) {
    lazyInit(node)
    node.anno['deadcode-liveness'] = data
  }

  Set<GraphNode> load(GraphNode node) {
    lazyInit(node)
    return node.anno['deadcode-liveness']
  }

  def gen(node) {
    return new LinkedHashSet(node.getUses())
  }

  def kill(node) {
    return Collections.singleton(node.getDef())
  }

  Set<GraphNode> transfer(GraphNode node, Set<GraphNode> input) {
    def out = gen(node)
    out.addAll(input - kill(node))
    return out
  }

  Set<GraphNode> join(GraphNode node) {
    def out = new HashSet()
    for (succ in node.successors) {
      out += load(succ)
    }
    return out
  }

  def run(startNode) {
    analize(startNode)
    eachNodeOf(startNode) {
      //don't delete the first node
      if (it.is(startNode)) return
      //can have side effects
      if (it instanceof LowIrCallOut || it instanceof LowIrPhi) {
        return
      }
      //could be div by 0, resulting in a runtime exception
      if (it instanceof LowIrBinOp && it.op == BinOpType.DIV) {
        return
      }
      //if the defined variable isn't live, delete this node
      def liveOut = it.successors.inject(new HashSet()) { set, succ ->
        set += load(succ)
      }
      if (it.getDef() != null && !(it.getDef() in liveOut)) {
        it.excise()
      }
      //if it's a noop, delete this node
      if (it.getClass() == LowIrNode.class || it.getClass() == LowIrValueNode.class) {
        if (it.metaText == 'continue') return
        it.excise()
      }
    }
  }
}
