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
/* On page 429 of Modern Comp impl in Java, it seems to indicate that the following optimization
is invalid because it would break the dominance property of the SSA graph. Therefore I'll disable
this code for now, unless we eventually determine it's okay. Instead, we can safely delete the
moves from undefined locations after "destroy all my beautiful hard work"
      } else if (node instanceof LowIrPhi) {
        def definedArgs = node.args.findAll{ it.defSite != null || it.type == TempVarType.PARAM}
        if (definedArgs.size() == 1) {
          new LinkedHashSet(node.getDef().useSites)*.replaceUse(node.getDef(), definedArgs[0])
        }
*/
      }
    }
  }
}
