package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class InductionVariableAnalysis {
  ValueNumberer valNum = new ValueNumberer()
  def loopStartNodes = []
  def basicInductionVars = []
  def foundComplexInductionVar = false
  def domComps = new DominanceComputations()

  def analize(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    domComps.computeDominators(startNode)

    eachNodeOf(startNode) { node ->
      node.predecessors.findAll{domComps.dominates(node, it)}.each {
        loopStartNodes << node
      }
    }

    //we can only analyze loops with distinct nodes
    assert new LinkedHashSet(loopStartNodes).size() == loopStartNodes.size()

    //now, we ensure that each loop has only a single exit point (i.e. no breaks)
    //to do this, we find the set of nodes reachable from the loop but not dominated by the loop
    //we ensure that there is exactly one edge leading into this set
    for (loopStartNode in loopStartNodes) {
      def exits = []
      def visited = new LinkedHashSet()
      def toVisit = new LinkedHashSet([loopStartNode])
      while (toVisit.size() != 0) {
        def cur = toVisit.iterator().next()
        toVisit.remove(cur)
        visited << cur
        if (!domComps.dominates(loopStartNode, cur)) {
          //found an exit node
          exits << cur
        } else {
          //found a loop node
          toVisit.addAll(cur.successors - visited)
        }
      }
      def loopExitCount = exits.collect{exit ->
        exit.predecessors.findAll{pred ->
          domComps.dominates(loopStartNode, pred)
        }
      }.flatten().size()
      assert loopExitCount == 1
    }

    def inductionVarList = findAllInductionVars()
    findBasicInductionVariables(inductionVarList)
  }

  def findBasicInductionVariables(LinkedHashSet inductionVarList) {
    def basicInductionVars = []
    inductionVarList.each { iv -> //iv == Induction Var
      //this is x+1
      def incExpr = new Expression(left: valNum.getExpr(iv.tmpVar.defSite), right: new Expression(constVal: 1), op: BinOpType.ADD)
      //this is all possible values of the induction var
      def ivArgs = iv.tmpVar.defSite.args
      //make sure the argument's tempvars are in the value numberer cache
      ivArgs*.defSite.each{valNum.getExpr(it)}
      //normal induction variables are either their lower bound or their induction step
      if (ivArgs.size() != 2) {
        foundComplexInductionVar = true
        return //can't determine anything further
      }
      //there are 2 possible values for the induction var, which are the lower bound and the
      //induction step, so make sure that one of the arguments is the induction step
      if (incExpr in ivArgs.collect{valNum.getExprOfTmp(it)}) {
        //this finds the phi tmp whose expression isn't the induction expr (this must be the lower bound
        iv.lowBoundTmp = ivArgs.find{ valNum.getExprOfTmp(it) != incExpr }

        //find the loop for which this is an induction variable
        def cur = iv.tmpVar.defSite //the phi function for the IV
        def loopStartNodes = loopStartNodes
        //find the first loop header which dominates the IV
        while (cur != null && !(cur in loopStartNodes)) cur = domComps.idom[cur]
        assert cur != null
        iv.beginLoop = cur

        //find the useSite of the incremented binop where it's being compared
        //get all comparison sites of the inc expr
        def compBinOps = ivArgs.find{ valNum.getExprOfTmp(it) == incExpr }
          .useSites.findAll{it instanceof LowIrBinOp && it.op == BinOpType.LT}
        //find the one which is comparing to the high bound
        for (compBinOp in compBinOps) {
          //find all the conditional jumps that the comparisons use
          def binOpJumpCandidates = compBinOp.tmpVar.useSites.findAll{it instanceof LowIrCondJump}
          //if any of the candidates have one edge which jumps back to the loop (dominated by head) and another that goes elsewhere
          if (binOpJumpCandidates.any{[it.trueDest, it.falseDest].findAll{dest->domComps.dominates(iv.beginLoop, dest)}.size() == 1}) {
             //this must be the induction binop, so we'll mark it
             assert iv.highBoundTmp == null
             iv.highBoundTmp = compBinOp.rightTmpVar
          }
        }
        if (iv.highBoundTmp) {
          basicInductionVars << iv
        } else {
          foundComplexInductionVar = true
        }
        println "iv is ${iv.tmpVar}, begin loop is ${iv.beginLoop.label}, high bound is ${iv.highBoundTmp}, low bound is ${iv.lowBoundTmp}"
      } else {
        foundComplexInductionVar = true
      }
    }
    println "Found complex induction vars: $foundComplexInductionVar"
  }

  LinkedHashSet findAllInductionVars() {
    def inductionVariableList = new LinkedHashSet()
    for (startLoop in loopStartNodes) {
      assert startLoop.predecessors.findAll{domComps.dominates(startLoop, it)}.size() == 1
      def endLoop = startLoop.predecessors.find{domComps.dominates(startLoop, it)}
      def cur = startLoop
      while (cur != endLoop) {
        if (cur instanceof LowIrPhi) {
          //is this phi just from an internal if statement?
          //check whether it has some argument which isn't dominated by the loop header
          if (cur.args.any{!domComps.dominates(startLoop, it.defSite)}) {
            inductionVariableList << new InductionVar(tmpVar: cur.tmpVar)
          }
        }
        cur = cur.successors[0]
      }
    }
    println "Induction variable candidate list: $inductionVariableList"
    return inductionVariableList
  }
}

class InductionVar {
  def beginLoop
  TempVar lowBoundTmp
  TempVar highBoundTmp
  TempVar tmpVar

  String toString() { "InductionVar(start: ${beginLoop?.label}, lowerBound: $lowBoundTmp, upperBound: $highBoundTmp, tmpVar: $tmpVar)" }
}
