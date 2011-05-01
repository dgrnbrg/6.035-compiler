package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class InductionVariableAnalysis {
  ValueNumberer valNum = new ValueNumberer()

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
//println "$inductionVarList"
    hasBasicInductionVariables(inductionVarList)
  }

  def hasLinearInductionVariables(LinkedHashSet inductionVarList) {
    
  }

  def hasBasicInductionVariables(LinkedHashSet inductionVarList) {
    inductionVarList.each { iv ->
      def incExpr = new Expression(left: valNum.getExpr(iv.tmpVar.defSite), right: new Expression(constVal: 1), op: BinOpType.ADD)
      if (incExpr in iv.tmpVar.defSite.args*.defSite.collect{valNum.getExpr(it)}) {
        println "this induction variable is basic and thus linear"
      }
    }
  }

  LinkedHashSet findInductionVars(LowIrNode startLoop, LowIrNode endLoop) {
    def inductionVariableList = new LinkedHashSet()
    def cur = startLoop
    while (cur != endLoop) {
      if (cur instanceof LowIrPhi) {
//TODO: incorrectly identifies variables of the if-statements as induction variables
        inductionVariableList << new InductionVar(tmpVar: cur.tmpVar)
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
  TempVar tmpVar
}
