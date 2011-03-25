package decaf
import decaf.graph.*

class TraceGraph {

  static String getColor(GraphNode n) {
    if(n.anno["traceMarker"] == null) {
      // default is white
      return "1.0 1.0 1.0"
    }

    // These are pastel-ish colors for readability.
    switch(n.anno["traceMarker"]) {
    case 1: 
      return "0.94 0.90 0.54" // khaki
    case 2:
      return "0.87 0.72 0.53" // burlywood
    case 3:
      return "0.82 0.41 0.12" // tan
    case 4: 
      return "1.0 0.65 0.0" // orange
    case 5:
      return "1.0 0.41 0.71" // hot pink
    case 6:
      return "0.93 0.51 0.93" // violet
    case 7: 
      return "0.0 1.0 0.50" // spring green
    case 8: 
      return "0.50 1.0 0.83" // aquamarine
    default:
      return "1.0 1.0 1.0" // white
    }
  }

  // This function traverses a lowir and marks the anno of each 
  // lowir node with trace-information.
  static void calculateTraces(GraphNode start) {
    // We are assuming that start is a lowir node.
    Set<GraphNode> visited = new HashSet<GraphNode>()
    Set<TraverserEdge> edges = new HashSet<TraverserEdge>()
    
    def newTraceStarts = [start]
    def curTraceMarker = 1

    while(newTraceStarts.size() > 0) {
      def curNode = newTraceStarts.first()

      curNode.anno["trace"] = [:]
      curNode.anno["trace"]["start"] = true

      while(curNode != null) {
        // mark curNode with the trace marker
        curNode.anno["traceMarker"] = curTraceMarker 

        if(curNode.anno["trace"] == null) {
          curNode.anno["trace"] = [:]
        }

        switch(curNode.getSuccessors().size()) {
        case(0): 
          // terminal node, hence:
          curNode.anno["trace"]["terminal"] = true
          curNode = null
          break;
        case(1):
          def nextNode = curNode.getSuccessors().first()
          if(nextNode.anno["traceMarker"]) {
            // the next node has already been visited.
            curNode.anno["trace"]["JmpSrc"] = nextNode.label
            nextNode.anno["trace"]["JmpDest"] = true
            curNode = null
          } else {
            curNode = nextNode;
          }
          break;
        case(2):
          assert(curNode instanceof LowIrCondJump);

          // add the true branch to newTraceStarts if it isn't already there.
          if(newTraceStarts.count(curNode.trueDest) == 0) {
            newTraceStarts = newTraceStarts + [curNode.trueDest];
          }

          if(curNode.falseDest.anno["traceMarker"]) {
            // we have already visited the falseDest
            curNode.anno["trace"]["FalseJmpSrc"] = true
            // we need to be able to jump to the false-dest though
            curNode.falseDest.anno["trace"]["JmpDest"] = true
            curNode = null
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
      curTraceMarker += 1      
    }
  }
}


// Notes:
// - Any low-ir node 
