package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class LoopInvariantCodeMotion {
  def run(methodDesc) {
    def startNode = methodDesc.lowir
    SSAComputer.updateDUChains(startNode)
    def loopAnal = new LoopAnalizer()
    loopAnal.run(startNode)
    def depAnal = new DependencyAnalizer()
    def loopNest = depAnal.computeLoopNest(loopAnal.loops)
    def worklist = depAnal.identifyInnermostLoops(loopAnal.loops)
    def visited = new HashSet()
    while (!worklist.isEmpty()) {
      def loop = worklist.pop()
      visited << loop

      def domComps = new DominanceComputations()
      domComps.computeDominators(startNode)
      //find all loop-invariant expressions
      def invariants = []
      loop.body.each { node ->
        if (node.getDef() == null) return //only relocate value-producing nodes
        //loads aren't invariant if there's a possible conflicting store
        if (node instanceof LowIrLoad &&
            loop.body.findAll{it instanceof LowIrStore && it.desc == node.desc}.size() > 0) return
        try {
          //try to use speculative motion
          def list = depAnal.isSpeculativelyMovableLoopInvariant(loop, node.getDef())
          invariants.addAll(list.findAll{!(it in invariants)})
        } catch (UnparallelizableException e) {
        }
      }
      //rewrite them all above the header
      invariants.findAll{!domComps.dominates(it, loop.header)}
      def landingPad = loop.header.predecessors.find{domComps.dominates(it, loop.header)}
      def copier = new Copier(methodDesc.tempFactory)
      copier.tmpCopyMap = new LazyMap({it})
      new LowIrBridge(invariants.collect{copier.nodeCopyMap[it]}).insertBefore(landingPad)
      SSAComputer.updateDUChains(startNode)

      if (loopNest[loop] != null && !(loopNest[loop] in visited) &&
          !(loopNest[loop] in worklist)) worklist << loopNest[loop]
    }
  }
}
