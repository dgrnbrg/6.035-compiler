package decaf
import decaf.graph.*

class TraceGraph {

  static String getColor(GraphNode n) {
    if(n.anno["traceMarker"] == null) {
      // default is white
      return "ghostwhite"
    }

    // These are pastel-ish colors for readability.
    // This is the X11 color-scheme, for more colors see:
    // http://www.graphviz.org/doc/info/colors.html
    // Yes I know it should just be an array and index into it.
    def traceColors = 
      ["aquamarine",
        "azure3",
        "bisque3",
        "brown1",
        "burlywood1",
        "lightblue",
        "chartreuse",
        "darkseagreen2",
        "deeppink",
        "deepskyblue1",
        "firebrick1",
        "gray62",
        "orange",
        "navajowhite2"]

    if(n.anno["traceMarker"] < traceColors.size())
      return traceColors[n.anno["traceMarker"]];
    return "ghostwhite"
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
