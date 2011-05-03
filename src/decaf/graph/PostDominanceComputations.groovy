package decaf.graph
import decaf.*
import static decaf.graph.Traverser.eachNodeOf

//So I decided to use the hash-equivalence to get this to work
//We just intercept the ordering with the ReverseGraphNode object
//The domComps in here will give you the postdominator tree
//Cool story bro
class PostDominanceComputations {
  def domComps = new DominanceComputations()

  def run(startNode) {
    def endNode = new LowIrNode(metaText: 'end node of graph')
    eachNodeOf(startNode) {
      if (it.successors.size() == 0) {
        LowIrNode.link(it, endNode)
      }
    }
    domComps.computeDominators(new ReverseGraphNode(node: endNode))
    domComps.computeDominanceFrontier(new ReverseGraphNode(node: endNode))
    for (pred in endNode.predecessors.clone()) {
        LowIrNode.unlink(pred, endNode)
    }
  }
}

class ReverseGraphNode implements GraphNode{
  GraphNode node

  List getPredecessors() {
    return node.getSuccessors().collect{new ReverseGraphNode(node: it)}
  }

  List getSuccessors() {
    return node.getPredecessors().collect{new ReverseGraphNode(node: it)}
  }

  int hashCode() {
    return node.hashCode()
  }

  boolean equals(Object other) {
    return other instanceof ReverseGraphNode && other.hashCode() == hashCode()
  }
}
