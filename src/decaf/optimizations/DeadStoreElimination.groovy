package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class DeadStoreElimination extends Analizer {
  DeadStoreElimination() {
    dir = AnalysisDirection.BACKWARD
  }

  void store(GraphNode node, Set data) {
  }

  Set load(GraphNode node) {
    return node.anno['deadstore-liveness']
  }

  def gen(node){
    if (node instanceof LowIrStore) {
      
    }
  }

  Set join(GraphNode node) {
    def out = new HashSet()
    return out
  }

  Set transfer(GraphNode node, Set input) {
    def out = gen(node)
    return out
  }
}
