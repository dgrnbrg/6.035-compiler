package decaf.optimizations
import decaf.*
import decaf.graph.*

class DependencyAnalizer {
  static def identifyInnermostLoops(loops) {
    def innermostLoops = []
    for (loop in loops) {
      //this loop's body contains no other loop's header
      if (loops.findAll{it != loop}.every{!(it.header in loop.body)}) {
        innermostLoops << loop
      }
    }
    return innermostLoops
  }

  static def identifyOutermostLoops(loops) {
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

  def extractReadsInSOPForm(loop, ivs, domComps) {
    return extractMemOpsInSOPForm(loop, ivs, domComps,
      loop.body.findAll{it instanceof LowIrLoad && it.index != null})
  }

  def extractWritesInSOPForm(loop, ivs, domComps) {
    return extractMemOpsInSOPForm(loop, ivs, domComps, loop.body.findAll{it instanceof LowIrStore})
  }

  def extractMemOpsInSOPForm(loop, ivs, domComps, nodesOfInterest) {
    def writes = []
    nodesOfInterest.each{ store ->
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
        try {
          isSpeculativelyMovableLoopInvariant(loop, it.getDef())
          return true
        } catch (UnparallelizableException e) {
          return false
        }
      }
      //check that it's adding a different induction variable
      def inductionVarsSoFar = new HashSet([null])
      def isDisjointInductionVar = { LowIrNode it ->
        inductionVarsSoFar.add(ivs.find{iv -> iv.tmpVar == it.getDef()})
      }
      def addr = new ArrayAccessAddress(node: store)
      for (LowIrNode it in products) {
        if (isInvariant(it)) {
          addr.invariants << it.getDef()
          continue
        }
        if (isDisjointInductionVar(it)) {
          addr.ivToInvariants[ivs.find{iv-> iv.tmpVar == it.getDef()}] << 1
          continue
        }
        if (it instanceof LowIrBinOp && it.op == BinOpType.MUL) {
          if (contractMoves(it.getUses()*.defSite).findAll{isInvariant(it)}.size() == 1 &&
              contractMoves(it.getUses()*.defSite).findAll{isDisjointInductionVar(it)}.size() == 1) {
            def iv = contractMoves(it.getUses()*.defSite).find{!isInvariant(it)}.getDef()
            iv = ivs.find{indVar-> indVar.tmpVar == iv}
            def invar = contractMoves(it.getUses()*.defSite).find{isInvariant(it)}
            addr.ivToInvariants[iv] << invar.getDef()
            continue
          }
        }
println "$it"
        throw new UnparallelizableException()
      }
      //at this point, we know that each of these is either a product, invariant, or iv
      println 'Write is:'
      println "  $addr"
      writes << addr
//      products.eachWithIndex {it, index -> if (index != products.size() - 1) println "  $it +" else println "  $it" }
    }
    return writes
  }

  def isSpeculativelyMovableLoopInvariant(outermostLoop, invariant) {
    assert outermostLoop.body.findAll{it instanceof LowIrStore && it.index == null}.size() == 0
    //first, we make sure this invariant is a binop combination of scalar loads and constants

    //the following algorithm is from the Topological Sorting wikipedia page
    def list = []
    def visited = new HashSet()
    def visit
    visit = { LowIrNode n ->
      if (n != null && !(n in visited) && (n in outermostLoop.body)) {
        visited << n
        n.getUses()*.defSite.each{visit(it)}
        if (n instanceof LowIrLoad && n.index != null) {
          def cur = n
          while (!(cur instanceof LowIrBoundsCheck) && cur.predecessors.size() == 1)
            cur = cur.predecessors[0]
          if (cur.predecessors.size() != 1) throw new UnparallelizableException()
          visit(cur)
        }
        list << n
      }
    }
    visit(invariant.defSite)

    //now, we have the nodes to relocate in the correct order
    //let's make sure they're of the correct form
    //division isn't relocatable
    if (!list.every{it instanceof LowIrBinOp || it instanceof LowIrLoad || it instanceof LowIrIntLiteral || it instanceof LowIrBoundsCheck})
      throw new UnparallelizableException()
    if (!list.findAll{it instanceof LowIrBinOp}.every{it.op != BinOpType.DIV && it.op != BinOpType.MOD})
      throw new UnparallelizableException()
    //TODO: do we need the following 2 lines for correctness?
//    if (!list.findAll{it instanceof LowIrLoad}.every{it.index == null})
//      throw new UnparallelizableException()

    //if all went well, return the list
    return list
  }

  def speculativelyMoveInnerLoopInvariantsToOuterLoop(startNode, outermostLoop, invariants) {
      
      def domComps2 = new DominanceComputations()
      domComps2.computeDominators(startNode)
      def tmps2 = new LinkedHashSet()
      Traverser.eachNodeOf(startNode) { tmps2 += it.getDef(); tmps2 += it.getUses() }
      tmps2.remove(null)
      if (!tmps2.every{ tmp -> tmp.useSites.every{domComps2.dominates(tmp.defSite, it)} }) {
        println "It was broke when I got here"
      }

    assert outermostLoop.body.findAll{it instanceof LowIrStore && it.index == null}.size() == 0
    for (TempVar invariant in invariants) {
      //first, we make sure this invariant is a binop combination of scalar loads and constants
      //then, we move it to the outermost loop's header
      //this is safe since the loop contains no scalar stores, so it's all speculatable

      def domComps = new DominanceComputations()
      domComps.computeDominators(startNode)
      if (domComps.dominates(invariant.defSite, outermostLoop.header)) continue

      def list = isSpeculativelyMovableLoopInvariant(outermostLoop, invariant)

      //now, we can unlink the list and relocate it to the loop header safely
      list*.excise()
      domComps = new DominanceComputations()
      domComps.computeDominators(startNode)
      assert outermostLoop.header.predecessors.size() == 2
      def landingPad = outermostLoop.header.predecessors.find{domComps.dominates(it, outermostLoop.header)}
      new LowIrBridge(list).insertBetween(landingPad, outermostLoop.header)
      outermostLoop.body.removeAll(list) //these aren't in this loop any more
      SSAComputer.updateDUChains(startNode)
//check that we broke nothing
      domComps = new DominanceComputations()
      domComps.computeDominators(startNode)
      def tmps = new LinkedHashSet()
      Traverser.eachNodeOf(startNode) { tmps += it.getDef(); tmps += it.getUses() }
      tmps.remove(null)
      if (!tmps.every{ tmp -> tmp.useSites.every{domComps.dominates(tmp.defSite, it)} }) {
        println "Relocated $invariant which broke stuff"
      }
    }
  }

  def generateParallelizabilityCheck(MethodDescriptor methodDesc, ArrayAccessAddress addr, Collection conflictingReadAddrs, LowIrNode trueDest, LowIrNode falseDest) {
    def constantSym = 22 //this is how we identify the constants in the map
    def ivToInvariant = [:] //this maps from iv_k to c_k
    def ivToInvariantReads = new LazyMap({[:]}) //this maps from iv_k to c_k for each read
    def genTmp = {methodDesc.tempFactory.createLocalTemp()}
    def instrs = []
    //first, we'll generate sums to ensure that the invariants are available in a single tmpVar each
    def sumListOfTmps = { list ->
      assert list.size() > 1
      def carriedTmp = list[0]
      for (int i = 1; i < list.size(); i++) {
        instrs << new LowIrBinOp(
          leftTmpVar: carriedTmp,
          rightTmpVar: list[i],
          tmpVar: genTmp(),
          op: BinOpType.ADD
        )
        carriedTmp = instrs[-1].tmpVar
      }
      return carriedTmp
    }
    //first, do it for the write
    if (addr.invariants.size() > 1) {
      ivToInvariant[constantSym] = sumListOfTmps(addr.invariants)
    } else if (addr.invariants.size() == 1) {
      ivToInvariant[constantSym] = addr.invariants[0]
    } else {
      instrs << new LowIrIntLiteral(value: 0, tmpVar: genTmp())
      ivToInvariant[constantSym] = instrs[-1].tmpVar
    }
    instrs << new LowIrIntLiteral(value: 1, tmpVar: genTmp())
    def constOneTmpVar = instrs[-1].tmpVar
    addr.ivToInvariants.each{ iv, invariants ->
      invariants = invariants.collect{ it == 1 ? constOneTmpVar : it }
      assert invariants.size() > 0
      if (invariants.size() > 1)
        ivToInvariant[iv] = sumListOfTmps(invariants)
      else
        ivToInvariant[iv] = invariants[0]
    }
    //now, for each read
    for (readAddr in conflictingReadAddrs) {
      if (readAddr.invariants.size() > 1) {
        ivToInvariantReads[readAddr][constantSym] = sumListOfTmps(addr.invariants)
      } else if (addr.invariants.size() == 1) {
        ivToInvariantReads[readAddr][constantSym] = addr.invariants[0]
      } else {
        instrs << new LowIrIntLiteral(value: 0, tmpVar: genTmp())
        ivToInvariantReads[readAddr][constantSym] = instrs[-1].tmpVar
      }
      readAddr.ivToInvariants.each{ iv, invariants ->
        invariants = invariants.collect{ it == 1 ? constOneTmpVar : it }
        assert invariants.size() > 0
        if (invariants.size() > 1)
          ivToInvariantReads[readAddr][iv] = sumListOfTmps(invariants)
        else
          ivToInvariantReads[readAddr][iv] = invariants[0]
      }
    }
    //now, we have summed the invariants, so we must now sort them
    def loopNest = []
    def loopNestMap = computeLoopNest((ivToInvariant.keySet() - constantSym)*.loop)
    if (loopNestMap.values().findAll{!(it in loopNestMap.keySet())}.size() == 1) {
      loopNest << loopNestMap.values().find{!(it in loopNestMap.keySet())}
      while (loopNest.size() != loopNestMap.size()+1) {
        loopNest << loopNestMap.find{it.value == loopNest[-1]}.key
      }
      loopNest = loopNest.collect{loop -> ivToInvariant.keySet().find{it != constantSym && it.loop == loop}}
    } else {
      loopNest << (ivToInvariant.keySet() - constantSym).iterator().next()
      assert loopNest[0] != null
    }
    loopNest << constantSym
    //now, we generate the comparisons for stride length calculations
    def mostRecentDest = trueDest
    for (int i = loopNest.size() - 2; i >= 0; i--) {
      def nestedStride = ivToInvariant[loopNest[i+1]]
      if (loopNest[i+1] != constantSym) {
        if (nestedStride != constOneTmpVar) {
          instrs << new LowIrBinOp(
            rightTmpVar: loopNest[i+1].highBoundTmp,
            leftTmpVar: nestedStride,
            tmpVar: genTmp(),
            op: BinOpType.MUL
          )
          nestedStride = instrs[-1].tmpVar
        } else {
          nestedStride = loopNest[i+1].highBoundTmp
        }
      }
      def cmpBridge = new LowIrValueBridge(new LowIrBinOp(
        rightTmpVar: nestedStride,
        leftTmpVar: ivToInvariant[loopNest[i]],
        tmpVar: genTmp(),
        op: BinOpType.GTE //since loop upper bound is exclusive
      ))
      def tmpFalseDest = falseDest
      if (GroovyMain.debug) {
        def msgLitNode = new LowIrStringLiteral(
          value: "failed test $i (%d >= %d)\\n",
          tmpVar: genTmp()
        )
        def msgCallNode = new LowIrCallOut(
          name: 'printf',
          paramTmpVars: [msgLitNode.tmpVar, nestedStride, ivToInvariant[loopNest[i]]],
          tmpVar: genTmp()
        )
        LowIrNode.link(msgLitNode, msgCallNode)
        LowIrNode.link(msgCallNode, falseDest)
        tmpFalseDest = msgLitNode
      }
      mostRecentDest = LowIrGenerator.static_shortcircuit(cmpBridge, mostRecentDest, tmpFalseDest)
    }
    //now we check that for each read, each IV's coefficient is >= the write's coefficient
    for (readAddr in conflictingReadAddrs) {
      //find all the write IVs which don't apply to this read
      //this means that the read must not be in the inner IV's loopbody
      def ivsToTest = ivToInvariant.keySet().findAll{it != constantSym && readAddr.node in it.loop.body}
println "IVS TO TEST = $ivsToTest"
println "ivToInvariant.keySet() = ${ivToInvariant.keySet()}"
      for (iv in ivsToTest) {
        //if read lacks this IV, force it to fail
        //otherwise, make sure read's coeff >= write's coeff
        if (!(iv in ivToInvariantReads[readAddr].keySet())) {
          def tmpFalseDest = falseDest
          if (GroovyMain.debug) {
            def msgLitNode = new LowIrStringLiteral(
              value: "failed test $i (%d >= %d)\\n",
              tmpVar: genTmp()
            )
            def msgCallNode = new LowIrCallOut(
              name: 'printf',
              paramTmpVars: [msgLitNode.tmpVar, nestedStride, ivToInvariant[loopNest[i]]],
              tmpVar: genTmp()
            )
            LowIrNode.link(msgLitNode, msgCallNode)
            LowIrNode.link(msgCallNode, falseDest)
            tmpFalseDest = msgLitNode
          }
          return tmpFalseDest //give up
        }

        if (ivToInvariantReads[readAddr][iv] == ivToInvariant[iv]) continue
        //check the coefficients
        def cmpBridge = new LowIrValueBridge(new LowIrBinOp(
          leftTmpVar: ivToInvariantReads[readAddr][iv],
          rightTmpVar: ivToInvariant[iv],
          tmpVar: genTmp(),
          op: BinOpType.GTE
        ))
        def tmpFalseDest = falseDest
        if (GroovyMain.debug) {
          def msgLitNode = new LowIrStringLiteral(
            value: "failed test for conflicting IV coefficient (%d >= %d)\\n",
            tmpVar: genTmp()
          )
          def msgCallNode = new LowIrCallOut(
            name: 'printf',
            paramTmpVars: [msgLitNode.tmpVar, ivToInvariantReads[readAddr][iv], ivToInvariant[iv]],
            tmpVar: genTmp()
          )
          LowIrNode.link(msgLitNode, msgCallNode)
          LowIrNode.link(msgCallNode, falseDest)
          tmpFalseDest = msgLitNode
        }
        mostRecentDest = LowIrGenerator.static_shortcircuit(cmpBridge, mostRecentDest, tmpFalseDest)
      }
      //now do it for constants
      if (ivToInvariantReads[readAddr][constantSym] != ivToInvariant[constantSym]) {
        def cmpBridge = new LowIrValueBridge(new LowIrBinOp(
          leftTmpVar: ivToInvariantReads[readAddr][constantSym],
          rightTmpVar: ivToInvariant[constantSym],
          tmpVar: genTmp(),
          op: BinOpType.GTE
        ))
        def tmpFalseDest = falseDest
        if (GroovyMain.debug) {
          def msgLitNode = new LowIrStringLiteral(
            value: "failed test for conflicting constant (%d >= %d)\\n",
            tmpVar: genTmp()
          )
          def msgCallNode = new LowIrCallOut(
            name: 'printf',
            paramTmpVars: [msgLitNode.tmpVar,
                           ivToInvariantReads[readAddr][constantSym],
                           ivToInvariant[constantSym]],
            tmpVar: genTmp()
          )
          LowIrNode.link(msgLitNode, msgCallNode)
          LowIrNode.link(msgCallNode, falseDest)
          tmpFalseDest = msgLitNode
        }
        mostRecentDest = LowIrGenerator.static_shortcircuit(cmpBridge, mostRecentDest, tmpFalseDest)
      }
    }

    def mainCheck = new LowIrBridge(instrs).seq(new LowIrBridge(mostRecentDest)).begin
    if (GroovyMain.debug) {
      def msgLitNode = new LowIrStringLiteral(
        value: "Too few loop iterations (only %d)\\n",
        tmpVar: genTmp()
      )
      def msgCallNode = new LowIrCallOut(
        name: 'printf',
        paramTmpVars: [msgLitNode.tmpVar, loopNest[0].highBoundTmp],
        tmpVar: genTmp()
      )
      LowIrNode.link(msgLitNode, msgCallNode)
      LowIrNode.link(msgCallNode, falseDest)
      falseDest = msgLitNode
    }
    //don't forget to ensure that the outer loop is at least 100? iterations
    def minIters = new LowIrIntLiteral(value: 100, tmpVar: genTmp())
    def minCmp = new LowIrBinOp(
      rightTmpVar: loopNest[0].highBoundTmp,
      leftTmpVar: minIters.tmpVar,
      tmpVar: genTmp(),
      op: BinOpType.LTE
    )
    return LowIrGenerator.static_shortcircuit(new LowIrBridge(minIters).seq(new LowIrValueBridge(minCmp)), mainCheck, falseDest)
  }
}

class UnparallelizableException extends Exception {
}

//The values in this object are TempVars (except for certain ivToIvariants values)
class ArrayAccessAddress {
  //this maps induction variables to a list of their invariants
  //for each plain copy of the IV (no multiply), we add the constant 1 to the list
  def ivToInvariants = new LazyMap({[]})
  //this is a list of the invariants w/o an induction variable
  def invariants = []

  //node this represents
  def node

  String toString() {
    "Access(invariants: $invariants, ivs: $ivToInvariants)"
  }
}
