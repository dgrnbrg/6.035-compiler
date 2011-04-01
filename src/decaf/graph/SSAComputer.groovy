package decaf.graph
import decaf.*
import static decaf.graph.Traverser.eachNodeOf

class SSAComputer {
  DominanceComputations domComps = new DominanceComputations()
  TempVarFactory tempFactory

  def compute(MethodDescriptor desc) {
    this.tempFactory = desc.tempFactory
    def startNode = desc.lowir
    doDominanceComputations(startNode)
    placePhiFunctions(startNode)
    rename(startNode)
  }

  def doDominanceComputations(startNode) {
    domComps.computeDominators(startNode)
    domComps.computeDominanceFrontier(startNode)
  }

  /**
  This implements Place-\Phi-Functions on page 407 of modern compiler impl. in java
  */
  def placePhiFunctions(LowIrNode startNode) {
    //A_orig is just the tmpVar of a LowIrValueNode, or empty
    def defsites = [:]
    def a_phi = [:]

    def a_orig = { node ->
      def tmpVar
      switch (node) {
      case LowIrValueNode:
        if (node.getClass() != LowIrValueNode.class)
          tmpVar = node.tmpVar
        break
      case LowIrMov:
        tmpVar = node.dst
        break
      }
      return tmpVar
    }

    eachNodeOf(startNode) { node ->
      //compute if it defined a variable
      def tmpVar = a_orig(node)
      if (tmpVar) {
        if (defsites[tmpVar] == null) {
          defsites[tmpVar] = [node]
        } else {
          assert !defsites[tmpVar].contains(node)
          defsites[tmpVar] << node
        }
      }
    }

    def insertBeforeMap = [:]

    // a is a TempVar (variable in book)
    for (a in defsites.keySet()) {
      def worklist = defsites[a].clone() ?: []
      while (worklist.size() > 0) {
        def n = worklist.pop()
        for (y in domComps.domFrontier[n]) {
          if (!(a_phi[y]?.contains(a))) { //if a_phi[y] is null or doesn't contain a
            def phi = new LowIrPhi(tmpVar: a, args: [a] * y.predecessors.size())
            if (insertBeforeMap[y]) {
              insertBeforeMap[y] << phi
            } else {
              insertBeforeMap[y] = [phi]
            }
            if (a_phi[y] == null) {
              a_phi[y] = [a]
            } else {
              a_phi[y] << a
            }
            if (!(y instanceof LowIrValueNode) || a_orig(y) != a) {
              worklist << y
            }
          }
        }
      }
    }

    insertBeforeMap.each { node, phis ->
      for (int i = 0; i < phis.size() - 1; i++) LowIrNode.link(phis[i], phis[i+1])
      def phiBridge = new LowIrBridge(phis[0], phis[-1])
      //this lets us pretend that the old merge node
      //is actually a block, since it knows where all its phi functions are
      phiBridge.insertBefore(node)
      //this is the newly inserted node that merges the phi functions
      phiBridge.begin.predecessors[0].anno['phi-functions'] = phis
      //this is the old node in the dominator tree that needs to have the phis visited first
      phiBridge.end.successors[0].anno['phi-functions-to-visit'] = phis
    }
  }

  /**
  This implements the rename function on p409 of modern compiler impl. in java
  */
  def renameStack = [:]
  def lazyInitRenameStack(x) {
    if (renameStack[x] == null) {
      renameStack[x] = [x]
    }
  }
  def mostRecentDefOf(x) {
    lazyInitRenameStack(x)
    return renameStack[x][-1]
  }
  def pushNewDefOf(x) {
    lazyInitRenameStack(x)
    def newDef = tempFactory.createLocalTemp()
    renameStack[x] << newDef
    return newDef
  }
  def rename(n, returnDefListAndDontPop = false) {
    def pushedDefs = []
    //visit phi functions that should come before it
    n.anno['phi-functions-to-visit']?.each{ pushedDefs += rename(it, true) }

    //only one statement per "block"
    if (!(n instanceof LowIrPhi)) {
      //replace uses
      for (var in n.getUses()) {
        n.replaceUse(var, mostRecentDefOf(var))
      }
    }
    //replace defs
    if (n.getDef()) {
      pushedDefs << n.getDef()
      n.replaceDef(n.getDef(), pushNewDefOf(n.getDef()))
    }

    //now, we're on "for each successor Y of block n"
    n.successors.each { y ->
      def j = y.predecessors.indexOf(n)
      y.anno['phi-functions'].each {
        it.args[j] = mostRecentDefOf(it.args[j])
      }
    }
    //for each child X of n
    if (!(n instanceof LowIrPhi)) {
      for (x in domComps.domTree.get(n)) {
        rename(x)
      }
    }
    if (returnDefListAndDontPop) {
      return pushedDefs
    } else {
      for (a in pushedDefs) {
        renameStack[a].pop()
      }
    }
  }

  /**
  Find all phi functions, then search through the predecessors to the join point
  Insert a move for each possibility at the join point, then delete the phi function
  */
  static void destroyAllMyBeautifulHardWork(LowIrNode startNode) {
    def phiFunctions = []
    def undefTmpVars = new LinkedHashSet()
    eachNodeOf(startNode) { node ->
      if (node instanceof LowIrPhi) phiFunctions << node
      if (node.getUses().defSite == null) undefTmpVars.addAll(node.getUses())
    }

    //for each phi
    for (phi in phiFunctions) {
      //find the join point
      def joinPoint = phi
      while (joinPoint.predecessors.size() == 1) joinPoint = joinPoint.predecessors[0]
      //we can't handle if a phi function expects an n-way join but finds an m-way join and m != n
      assert phi.args.size() == joinPoint.predecessors.size()

      //insert the appropriate moves
      joinPoint.predecessors.clone().eachWithIndex { pred, index ->
        //we only emit moves for phi arguments that were defined
        def mov
        if (phi.args[index].defSite != null) {
          mov = new LowIrMov(src: phi.args[index], dst: phi.tmpVar)
        } else {
          //TODO: make these be inline
          mov = new LowIrIntLiteral(value: 0, tmpVar: phi.tmpVar)
        }
        new LowIrBridge(mov).insertBetween(pred, joinPoint)
      }

      assert phi.successors.size() == 1
      if (phi.predecessors.size() == 1) {
        if (phi.successors[0]) LowIrNode.link(phi.predecessors[0], phi.successors[0])
        LowIrNode.unlink(phi.predecessors[0], phi)
      } else if (phi.predecessors.size() > 1) {
        for (pred in phi.predecessors.clone()) {
          if (phi.successors[0]) LowIrNode.link(pred, phi.successors[0])
          LowIrNode.unlink(pred, phi)
        }
      }
      if (phi.successors) LowIrNode.unlink(phi, phi.successors[0])
    }

    assert startNode.successors.size() == 1
    for (tmpVar in undefTmpVars) {
      new LowIrBridge(new LowIrIntLiteral(value: 0, tmpVar: tmpVar)).insertBetween(startNode, startNode.successors[0])
    }
  }
}
