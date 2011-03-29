package decaf.graph
import decaf.*

//TODO: test multi-way merging (e.g. for loop with 3+ breaks)
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

    //for each node (worklist algorithm)
    def unvisitedNodes = new LinkedHashSet([startNode])
    while (unvisitedNodes.size() != 0) {
      def node = unvisitedNodes.iterator().next()
      unvisitedNodes.remove(node)
      //mark and add unvisited to list
      node.anno['ssa-foreach-mark'] = true
      node.successors.each {
        if (!(it.anno['ssa-foreach-mark'])) {
          unvisitedNodes << it
        }
      }

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
    def tmpVar
    if (!(n instanceof LowIrPhi)) {
      //replace uses
      switch (n) {
      case LowIrCondJump:
        tmpVar = mostRecentDefOf(n.condition)
        n.condition = tmpVar
        tmpVar.useSites << n
        break
      case LowIrCallOut:
      case LowIrMethodCall:
        for (int i = 0; i < n.paramTmpVars.size(); i++) {
           tmpVar = mostRecentDefOf(n.paramTmpVars[i])
           n.paramTmpVars[i] = tmpVar
           tmpVar.useSites << n
        }
        break
      case LowIrReturn:
        if (n.tmpVar) {
          tmpVar = mostRecentDefOf(n.tmpVar)
          n.tmpVar = tmpVar
          tmpVar.useSites << n
        }
        break
      case LowIrBinOp:
        tmpVar = mostRecentDefOf(n.leftTmpVar)
        n.leftTmpVar = tmpVar
        tmpVar.useSites << n
        if (n.rightTmpVar) {
          tmpVar = mostRecentDefOf(n.rightTmpVar)
          n.rightTmpVar = tmpVar
          tmpVar.useSites << n
        }
        break
      case LowIrMov:
        tmpVar = mostRecentDefOf(n.src)
        n.src = tmpVar
        tmpVar.useSites << n
        break
      case LowIrStore: //continues into LowIrLoad for the index
        tmpVar = mostRecentDefOf(n.value)
        n.value = tmpVar
        tmpVar.useSites << n
      case LowIrLoad:
        tmpVar = mostRecentDefOf(n.index)
        n.index = tmpVar
        tmpVar.useSites << n
        break
      case LowIrIntLiteral:
      case LowIrStringLiteral:
        break
      default:
        if (n.getClass() == LowIrNode.class || n.getClass() == LowIrValueNode.class) break
        assert false
      }
    }
    //replace defs
    switch (n) {
      case LowIrValueNode:
        if (n.getClass() == LowIrValueNode.class) {
          //this is a noop, we should ignore it
          break
        }
        pushedDefs << n.tmpVar
        tmpVar = pushNewDefOf(n.tmpVar)
        n.tmpVar = tmpVar
        tmpVar.defSite = n
        break
      case LowIrMov:
        pushedDefs << n.dst
        tmpVar = pushNewDefOf(n.dst)
        n.dst = tmpVar
        tmpVar.defSite = n
        break
      default:
        break
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
    //for each node (worklist algorithm)
    def unvisitedNodes = [startNode]
    while (unvisitedNodes.size() != 0) {
      def node = unvisitedNodes.pop()
      //mark and add unvisited to list
      node.anno['de-ssa-foreach-mark'] = true
      node.successors.each {
        if (!(it.anno['de-ssa-foreach-mark'])) {
          unvisitedNodes << it
        }
      }

      if (node instanceof LowIrPhi) phiFunctions << node
    }

    //for each phi
    for (phi in phiFunctions) {
      //find the join point
      def joinPoint = phi
      while (joinPoint.predecessors.size() == 1) joinPoint = joinPoint.predecessors[0]
      //we can't handle if a phi function expects an n-way join but finds an m-way join and m != n
      assert phi.args.size() == joinPoint.predecessors.size()

      //insert the appropriate moves
      def moves = [] //this contains tuples of [LowIrMov, predecessor]
      joinPoint.predecessors.eachWithIndex { pred, index ->
        moves << [new LowIrMov(src: phi.args[index], dst: phi.tmpVar), pred]
      }
      moves.each {tuple -> 
        def mov = tuple[0]
        def pred = tuple[1]
        new LowIrBridge(mov).insertBetween(pred, joinPoint)
      }

      //delete the phi function
      assert phi.predecessors.size() == 1 && phi.successors.size() == 1
      LowIrNode.link(phi.predecessors[0], phi.successors[0])
      LowIrNode.unlink(phi.predecessors[0], phi)
      LowIrNode.unlink(phi, phi.successors[0])
    }
  }
}
