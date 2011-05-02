package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class InductionVariableAnalysis {
  ValueNumberer valNum = new ValueNumberer()
  def basicInductionVars = []
  def foundComplexInductionVar = false
  def domComps = new DominanceComputations()
  def loopAnal = new LoopAnalizer()

  def analize(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    domComps.computeDominators(startNode)
    loopAnal.run(startNode)

    //we can only analyze loops with distinct nodes
    def loopStartNodes = loopAnal.loops.collect{it.header}
    assert new LinkedHashSet(loopStartNodes).size() == loopStartNodes.size()

    def inductionVars = findAllInductionVars(loopAnal.loops)
    findBasicInductionVariables(inductionVars)
  }

  def findBasicInductionVariables(Collection inductionVars) {
    def basicInductionVars = []
    inductionVars.each { iv -> //iv == Induction Var
      //this is x+1
      def incExpr = new Expression(left: valNum.getExpr(iv.tmpVar.defSite), right: new Expression(constVal: 1), op: BinOpType.ADD)
      //this is all possible values of the induction var
      def ivArgs = iv.tmpVar.defSite.args
      //make sure the argument's tempvars are in the value numberer cache
      ivArgs*.defSite.each{valNum.getExpr(it)}
      //normal induction variables are either their lower bound or their induction step
      if (ivArgs.size() != 2) {
        foundComplexInductionVar = true
        println "$iv has more than 2 possible in-edges"
        return //can't determine anything further
      }
      //there are 2 possible values for the induction var, which are the lower bound and the
      //induction step, so make sure that one of the arguments is the induction step
      if (incExpr in ivArgs.collect{valNum.getExprOfTmp(it)}) {
        //this finds the phi tmp whose expression isn't the induction expr (this must be the lower bound
        iv.lowBoundTmp = ivArgs.find{ valNum.getExprOfTmp(it) != incExpr }

        //find the high bound, which will be the right argument to the binop whose result determines the loop exit condition
        def compBinOp = iv.loop.exit.condition.defSite
        assert compBinOp.op == BinOpType.LT
        //if we have (iv+1 < invariant)
        if (valNum.getExprOfTmp(compBinOp.leftTmpVar) == incExpr &&
            domComps.dominates(compBinOp.rightTmpVar.defSite, iv.loop.header)) {
          iv.highBoundTmp = compBinOp.rightTmpVar
        }

        if (iv.highBoundTmp) {
          basicInductionVars << iv
        } else {
          println "couldn't determine high bound of $iv"
          foundComplexInductionVar = true
        }
        println "iv is ${iv.tmpVar}, begin loop is ${iv.loop.header.label}, high bound is ${iv.highBoundTmp}, low bound is ${iv.lowBoundTmp}"
      } else {
        println "$iv isn't of the form x <- x + 1"
        foundComplexInductionVar = true
      }
    }
    println "Found complex induction vars: $foundComplexInductionVar"
  }

  Collection findAllInductionVars(loops) {
    def inductionVariableList = []
    for (loop in loops) {
      loop.body.each{ cur ->
        if (cur instanceof LowIrPhi) {
          //is this phi just from an internal if statement?
          //check whether it has some argument which isn't dominated by the loop header
          if (cur.args.any{!domComps.dominates(loop.header, it.defSite)}) {
            inductionVariableList << new InductionVar(tmpVar: cur.tmpVar, loop: loop)
          }
        }
      }
    }
    println "Induction variable candidate list: $inductionVariableList"
    return inductionVariableList
  }
}

class InductionVar {
  def loop
  TempVar lowBoundTmp
  TempVar highBoundTmp
  TempVar tmpVar

  String toString() { "InductionVar(start: $loop, lowerBound: $lowBoundTmp, upperBound: $highBoundTmp, tmpVar: $tmpVar)" }
}
