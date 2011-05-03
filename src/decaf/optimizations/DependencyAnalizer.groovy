package decaf.optimizations
import decaf.*
import decaf.graph.*

class DependencyAnalizer {
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
}

class UnparallelizableException extends Exception {
}
