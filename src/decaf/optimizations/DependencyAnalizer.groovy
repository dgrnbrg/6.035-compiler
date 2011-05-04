package decaf.optimizations
import decaf.*
import decaf.graph.*

class DependencyAnalizer {
  def identifyInnermostLoops(loops) {
    def innermostLoops = []
    for (loop in loops) {
      //this loop's body contains no other loop's header
      if (loops.findAll{it != loop}.every{!(it.header in loop.body)}) {
        innermostLoops << loop
      }
    }
    return innermostLoops
  }

  def identifyOutermostLoops(loops) {
    def outermostLoops = []
    for (loop in loops) {
      //no other loop's body contains this loop's header
      if (loops.findAll{it != loop}.every{!(loop.header in it.body)}) {
        outermostLoops << loop
      }
    }
    return outermostLoops
  }

  def computeLoopNest(loops) {
    def outerMost = identifyOutermostLoops(loops)
    def inToOut = [:]
    loops = loops - outerMost
    def lastLevel = outerMost
    while (!loops.isEmpty()) {
      def nextMost = identifyOutermostLoops(loops)
      nextMost.each { inner ->
        inToOut[inner] = lastLevel.findAll{outer -> outer.body.contains(inner.header)}
        assert inToOut[inner].size() == 1
        inToOut[inner] = inToOut[inner][0]
      }
      lastLevel = nextMost
      loops.removeAll(lastLevel)
    }
    return inToOut
  }

  def extractWritesInSOPForm(loop, ivs, domComps) {
    loop.body.findAll{it instanceof LowIrStore}.each{ store ->
      //stores must be to arrays
      if (store.index == null) throw new UnparallelizableException()
      //makes moves become their source
      def contractMoves = { list -> list.collect{node ->
        while (node instanceof LowIrMov) node = node.src.defSite
        return node
      }}
      def sums = contractMoves([store.index.defSite])
      def products = []
      while (!sums.isEmpty() && sums[-1] instanceof LowIrBinOp && sums[-1].op == BinOpType.ADD) {
        //for each sum's operand, contract the defsite and then add it to the sum or product list
        contractMoves(sums.pop().getUses()*.defSite).each {
          if (it instanceof LowIrBinOp && it.op == BinOpType.ADD) {
            //it's a continuation of the sum
            sums << it
          } else {
            //it could be a product
            products << it
          }
        }
      }
      //the following line handles when there's no sum
      if (sums.size() == 1) products << sums.pop()
      if (!sums.isEmpty()) throw new UnparallelizableException()
      //check that the products are all either invariant or an induction var * an invariant or an induction var
      def isInvariant = { LowIrNode it ->
        domComps.dominates(it, loop.header) || it instanceof LowIrIntLiteral
      }
      //check that it's adding a different induction variable
      def inductionVarsSoFar = new HashSet([null])
      def isDisjointInductionVar = { LowIrNode it ->
        inductionVarsSoFar.add(ivs.find{iv -> iv.tmpVar == it.getDef()})
      }
println "Looking at products $products"
      for (LowIrNode it in products) {
        if (isInvariant(it)) continue
        if (isDisjointInductionVar(it)) continue
        if (it instanceof LowIrBinOp && it.op == BinOpType.MUL) {
          if (contractMoves(it.getUses()*.defSite).findAll{isInvariant(it)}.size() == 1 &&
              contractMoves(it.getUses()*.defSite).findAll{isDisjointInductionVar(it)}.size() == 1) {
            continue
          }
        }
        println "Failed on $it"
        throw new UnparallelizableException()
      }
      println 'Write is:'
      products.eachWithIndex {it, index -> if (index != products.size() - 1) println "  $it +" else println "  $it" }
    }
  }

  def speculativelyMoveInnerLoopInvariantsToOuterLoop(startNode, outermostLoop, invariants) {
    assert outermostLoop.body.findAll{it instanceof LowIrStore && it.index == null}.size() == 0
    for (TempVar invariant in invariants) {
      //first, we make sure this invariant is a binop combination of scalar loads and constants
      //then, we move it to the outermost loop's header
      //this is safe since the loop contains no scalar stores, so it's all speculatable

      //the following algorithm is from the Topological Sorting wikipedia page
      def list = []
      def visited = new HashSet()
      def visit
      visit = { LowIrNode n ->
        if (!(n in visited)) {
          visited << n
          n.getUses()*.defSite.each{visit(it)}
          list << n
        }
      }
      visit(invariant.defSite)

      //now, we have the nodes to relocate in the correct order
      //let's make sure they're of the correct form
      //division isn't relocatable
      if (!list.every{it instanceof LowIrBinOp || it instanceof LowIrLoad || it instanceof LowIrIntLiteral})
        throw new UnparallelizableException()
      if (!list.findAll{it instanceof LowIrBinOp}.every{it.op != BinOpType.DIV})
        throw new UnparallelizableException()
      if (!list.findAll{it instanceof LowIrLoad}.every{it.index == null})
        throw new UnparallelizableException()

      //now, we can unlink the list and relocate it to the loop header safely
      list*.excise()
      def domComps = new DominanceComputations()
      domComps.computeDominators(startNode)
      assert outermostLoop.header.predecessors.size() == 2
      def landingPad = outermostLoop.header.predecessors.find{domComps.dominates(it, outermostLoop.header)}
      new LowIrBridge(list).insertBetween(landingPad, outermostLoop.header)
      SSAComputer.updateDUChains(startNode)
    }
  }
}

class UnparallelizableException extends Exception {
}
