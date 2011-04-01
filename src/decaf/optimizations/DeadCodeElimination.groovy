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

  void store(GraphNode node, Set data) {
    lazyInit(node)
    node.anno['deadcode-liveness'] = data
  }

  Set load(GraphNode node) {
    lazyInit(node)
    return node.anno['deadcode-liveness']
  }

  def gen(node) {
    return new LinkedHashSet(node.getUses())
  }

  def kill(node) {
    return Collections.singleton(node.getDef())
  }

  Set transfer(GraphNode node, Set input) {
    def out = gen(node)
    out.addAll(input - kill(node))
    return out
  }

  Set join(GraphNode node) {
    def out = new HashSet()
    for (succ in node.successors) {
      out += load(succ)
    }
    return out
  }

  def run(startNode) {
    analize(startNode)
    def worklist = new LinkedHashSet()
    eachNodeOf(startNode) {
      //don't delete the first node
      if (it.is(startNode)) return
      //can have side effects
      if (it instanceof LowIrCallOut || it instanceof LowIrMethodCall) {
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
        worklist << it
      }
      //deleting noops seems to cause weird bugs, so we won't do that any more
    }
    while (worklist.size() > 0) {
      def node = worklist.iterator().next()
      worklist.remove(node)
      def uses = node.getUses()
      node.excise()
      worklist += uses.findAll{it.useSites.size() == 0}*.defSite
    }
  }
}