package decaf.graph

enum AnalysisDirection {
  FORWARD, BACKWARD
}

abstract class Analizer {
  AnalysisDirection dir = AnalysisDirection.FORWARD

  //transfer returns true if something changed
  abstract Set<GraphNode> transfer(GraphNode node, Set<GraphNode> input);
  abstract Set<GraphNode> join(GraphNode node);

  abstract void store(GraphNode node, Set<GraphNode> data);
  abstract Set<GraphNode> load(GraphNode node);

  void analize(GraphNode startNode) {
    def worklist = new LinkedHashSet(allNodes(startNode))
    while (worklist.size() != 0) {
      def node = worklist.iterator().first()
      worklist.remove(node)
      def old = load(node).clone()
      def input = join(node)
      def out = transfer(node, input)
      if (old != out) {
        if (dir == AnalysisDirection.FORWARD) {
          worklist.addAll(node.getSuccessors())
        } else if (dir == AnalysisDirection.BACKWARD) {
          worklist.addAll(node.getPredecessors())
        } else {
          assert false
        }
      }
    }
  }
}
