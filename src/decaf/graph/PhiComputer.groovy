package decaf.graph
import decaf.*

class SSAComputer {

  /**
  This implements Place-\Phi-Functions on page 407 of modern compiler impl. in java
  We only place phi functions for variables that are defined more than once,
  even if it's on the same path (so that phi function would be spurious).
  */
  def placePhiFunctions(LowIrNode startNode) {
    //A_orig is just the tmpVar of a LowIrValueNode, or empty
    DominanceComputations domComps = new DominanceComputations()
    domComps.computeDominators(startNode)
    domComps.computeDominanceFrontier(startNode)
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
    def unvisitedNodes = [startNode]
    while (unvisitedNodes.size() != 0) {
      def node = unvisitedNodes.pop()
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
            if (defsites[a].size() > 1) { //if not already static single assignment
              def phi = new LowIrPhi(tmpVar: a, args: [a] * y.predecessors.size())
              if (insertBeforeMap[y]) {
                insertBeforeMap[y] << phi
              } else {
                insertBeforeMap[y] = [phi]
              }
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
      new LowIrBridge(phis[0], phis[-1]).insertBefore(node)
    }
  }
}
