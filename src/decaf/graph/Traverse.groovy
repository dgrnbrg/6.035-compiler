package decaf.graph

class TraverserEdge {
  GraphNode src, dst

  boolean equals(Object o) {
    if (o instanceof TraverserEdge) {
      return o.src.is(src) && o.dst.is(dst)
    }
    return false
  }

  int hashCode() {
    return src.hashCode() + 17*dst.hashCode()
  }
}

abstract class Traverser {
  Set<GraphNode> visited = new HashSet<GraphNode>()

  Set<TraverserEdge> edges = new HashSet<TraverserEdge>()

  abstract void visitNode(GraphNode n);
  abstract void link(GraphNode src, GraphNode dst);

  void reset() {
    visited.clear()
    edges.clear()
  }

  // Specifically for passes ran by dataflow analyses
  void analyze(TransferFunction trans){
    def cur = start
    def queue = []
    while (cur != null) {
      //trans.transfer(cur)
      println ">> analyze:${cur}"
      visited.add(cur)
      def intermediate = (cur.successors - visited)
      queue += intermediate
      edges.addAll(cur.successors.collect{new TraverserEdge(src:cur, dst:it)})
      cur = queue ? queue.pop() : null
    }
    edges.each{ link(it.src, it.dst) }
  }

  // traverse() isn't sufficient for dataflow analysis due to the lack
  // of ability for the programmer to define which analysis method to
  // run against the node.
  void traverse(GraphNode start) {
    def cur = start
    def queue = []
    while (cur != null) {
      visitNode(cur)
      visited.add(cur)
      def intermediate = (cur.successors - visited)
      queue += intermediate
      edges.addAll(cur.successors.collect{new TraverserEdge(src:cur, dst:it)})
      cur = queue ? queue.pop() : null
    }
    edges.each{ link(it.src, it.dst) }
  }
}
