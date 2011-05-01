package decaf
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class InductionVariableAnalysis {

  def analize(MethodDescriptor methodDesc) {
    def startLoop
    def endLoop
    def startNode = methodDesc.lowir
    def domComps = new DominanceComputations()
    domComps.computeDominators(startNode)

    eachNodeOf(startNode) { node ->
      def dominated = {
        def cur = it
        while (cur != null && cur != node) cur = domComps.idom[cur]
        return cur == node
      }
      node.predecessors.findAll{dominated(it)}.each {
//println "found the loop: $it, node $node"
        startLoop = node
        endLoop = it
      }
    }
    def inductionVarList = findInductionVars(startLoop, endLoop)
println "$inductionVarList"
  }

  def hasBasicInductionVariables(LowIrNode startLoop) {
    
  }

  LinkedHashSet findInductionVars(LowIrNode startLoop, LowIrNode endLoop) {
    def inductionVariableList = new LinkedHashSet()
    def cur = startLoop
    while (cur != endLoop) {
      if (cur instanceof LowIrPhi) {
        inductionVariableList << cur.tmpVar
      }
      cur = cur.successors[0]
    }
    return inductionVariableList
  }
}

class InductionVar {
  def beginLoop
//  def endLoop
  TempVar lowBoundTmp
  TempVar highBoundTmp
}
