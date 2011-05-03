package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class AggressiveDCE {
  def run(LowIrNode startNode) {
    SSAComputer.updateDUChains(startNode)
    def pdc = new PostDominanceComputations()
    pdc.run(startNode)
    def liveNodes = new LinkedHashSet()
    def conditionals = []
    //first, mark live all calls, stores, returns
    eachNodeOf(startNode) {
      switch (it) {
      case LowIrCondJump:
        conditionals << it
      case LowIrMethodCall:
      case LowIrCallOut:
      case LowIrStore:
      case LowIrReturn:
      case LowIrBoundsCheck:
        liveNodes << it
        break
      }
      if (it.getClass() == LowIrNode.class || it.getClass() == LowIrValueNode.class) liveNodes << it
    }
    def worklist = new LinkedHashSet(liveNodes)
    while (!worklist.isEmpty()) {
      def cur = worklist.iterator().next()
      worklist.remove(cur)
      //add all nodes that produce a result used by this node
      def deps = cur.getUses().collect{it.defSite}.findAll{it != null}
      //add all conditionals which we are control-dependent on
      deps.addAll(conditionals.findAll{cur in pdc.domComps.domFrontier[it]})
      deps.removeAll(liveNodes)
      worklist.addAll(deps)
      liveNodes.addAll(deps)
    }
    eachNodeOf(startNode) {
      if (!(it in liveNodes)) {
        it.excise()
      }
    }
  }
}
