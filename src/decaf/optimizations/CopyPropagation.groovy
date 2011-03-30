package decaf.optimizations
import decaf.*
import static decaf.graph.Traverser.eachNodeOf

class CopyPropagation {
  def propagate(LowIrNode startNode) {
    eachNodeOf(startNode) { node ->
      if (node instanceof LowIrMov) {
        def u = new LinkedHashSet(node.getDef().useSites)
        def results = u*.replaceUse(node.dst, node.src)
        assert results.every{ it > 0 }
      } else if (node instanceof LowIrPhi) {
        def definedArgs = node.args.findAll{ it.defSite != null && it.type == TempVarType.LOCAL}
        if (definedArgs.size() == 1) {
          new LinkedHashSet(node.getDef().useSites)*.replaceUse(node.tmpVar, definedArgs[0])
        }
      }
    }
  }
}
