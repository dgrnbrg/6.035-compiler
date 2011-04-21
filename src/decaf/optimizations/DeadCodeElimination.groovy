package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class DeadCodeElimination extends Analizer {
  DeadCodeElimination() {
    dir = AnalysisDirection.BACKWARD
  }

  def map = new LazyMap({new HashSet()})

  void store(GraphNode node, Set data) {
    map[node] = data
  }

  Set load(GraphNode node) {
    return map[node]
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

  boolean sideEffectFree(LowIrNode it) {
    //can have side effects
    if (it instanceof LowIrCallOut || it instanceof LowIrMethodCall) {
      return false
    }
    //could be div by 0, resulting in a runtime exception
    if (it instanceof LowIrBinOp && it.op == BinOpType.DIV) {
      return false
    }

    return true
  }

  def run(startNode) {
    //We initialize the worklist in reverse to allow the propagator to visit the nodes
    //in closer to linear time than n^2/2 time
    worklistInit = { k ->
      def worklist = []
      eachNodeOf(k) { worklist << it }
      def worklist_final = new LinkedHashSet()
      for (int i = worklist.size() - 1; i >= 0; i--) {
        worklist_final << worklist[i]
      }
      return worklist_final
    }
    analize(startNode)
    def worklist = new LinkedHashSet()
    eachNodeOf(startNode) {
      //don't delete the first node
      if (it.is(startNode)) return
      if (!sideEffectFree(it)) return
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
      if (sideEffectFree(node) && !node.is(startNode)) {
        node.excise()
      }
      worklist += uses.findAll{it.useSites.size() == 0}*.defSite.findAll{it != null && sideEffectFree(it)}
    }
  }
}
