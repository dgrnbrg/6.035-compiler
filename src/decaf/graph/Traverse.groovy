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

  // The structure of this function is very similar to the structure of 
  // the calculateTraces function in Trace.groovy. 
  // NOTE: This function assumes that calculateTraces was run on the 
  // lowir that is being traversed.
  void traverseWithTraces(GraphNode start) {
    def num50 = 0;
    assert(start.anno["trace"]["start"]);

    def newTraceStarts = [start]
    
    while(newTraceStarts.size() > 0) {
      def curNode = newTraceStarts.first()
      assert(curNode.anno["traceMarker"])
      def curTraceMarker = curNode.anno["traceMarker"]

      while(curNode != null) {
        visitNode(curNode);

        switch(curNode.getSuccessors().size()) {
        case(0):
          // terminal node
          curNode = null;
          //println("Stopping tracing here for traceMarker = $curTraceMarker");
          break;
        case(1): 
          def nextNode = curNode.getSuccessors().first()

          if(curNode.anno["trace"]["JmpSrc"]) {
            // Stop the tracing here.
            curNode = null
            //println("Stopping tracing here for traceMarker = $curTraceMarker");
          } else {
            curNode = nextNode;
          }
          break; 
        case(2):
          //assert(curNode instanceof LowIrCondJump);

          // add the true branch to newTraceStarts if it isn't already there.
          if(newTraceStarts.count(curNode.trueDest) == 0) {
            newTraceStarts = newTraceStarts + [curNode.trueDest];
          }

          if(curNode.anno["trace"]["FalseJmpSrc"]) {
            // Stop the tracing here.
            curNode = null
            //println("Stopping tracing here for traceMarker = $curTraceMarker");
          } else {
            curNode = curNode.falseDest;
          }
          break;
        default:
          assert(false);
        }
      }
      // Now we are done with this trace, so get rid of it.
      newTraceStarts = newTraceStarts.tail()
    }

  }

  static void eachNodeOf(start, closure) {
    //for each node (worklist algorithm)
    def unvisitedNodes = new LinkedHashSet([start])
    def visitedNodes = new HashSet()
    while (unvisitedNodes.size() != 0) {
      def node = unvisitedNodes.iterator().next()
      unvisitedNodes.remove(node)
      //mark and add unvisited to list
      visitedNodes << node
      node.successors.each {
        if (!(visitedNodes.contains(it))) {
          unvisitedNodes << it
        }
      }

      closure(node)
    }
  }
}
