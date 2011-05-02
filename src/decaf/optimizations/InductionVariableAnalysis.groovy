package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class InductionVariableAnalysis {
  ValueNumberer valNum = new ValueNumberer()
  def startLoop
  def endLoop

  def analize(MethodDescriptor methodDesc) {
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
    def basicInductionVars = []
    inductionVarList.each { iv ->
      def incExpr = new Expression(left: valNum.getExpr(iv.tmpVar.defSite), right: new Expression(constVal: 1), op: BinOpType.ADD)
      def ivArgs = iv.tmpVar.defSite.args
      ivArgs*.defSite.each{valNum.getExpr(it)}
      assert ivArgs.size() == 2
      if (incExpr in ivArgs.collect{valNum.getExprOfTmp(it)}) {
        iv.lowBoundTmp = ivArgs.find{ valNum.getExprOfTmp(it) != incExpr }
        def cond = endLoop.condition
        def condDef = cond.defSite
        if (condDef.op == BinOpType.LT &&
            condDef.leftTmpVar == ivArgs.find{s ->
              valNum.getExprOfTmp(s) == incExpr}) {
          iv.highBoundTmp = condDef.rightTmpVar
        }
/*        iv.highBoundTmp = ivArgs.find{ s->
          valNum.getExprOfTmp(s) == incExpr }
            .useSites.find{it.op == BinOpType.LT}.rightTmpVar*/
//println "iv is ${iv.tmpVar}, high bound is ${iv.highBoundTmp}, low bound is ${iv.lowBoundTmp}"
        basicInductionVars << iv
      }
    }
  }

  LinkedHashSet findInductionVars(LowIrNode startLoop, LowIrNode endLoop) {
    def inductionVariableList = new LinkedHashSet()
    def cur = startLoop
    while (cur != endLoop) {
      if (cur instanceof LowIrPhi) {
//TODO: incorrectly identifies variables of the if-statements as induction variables
// but maybe it doesn't matter because we determine whether the induction variables are
//basic and whether they are linear
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
