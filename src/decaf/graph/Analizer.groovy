package decaf.graph
import static decaf.graph.Traverser.eachNodeOf

enum AnalysisDirection {
  FORWARD, BACKWARD
}

abstract class Analizer {
  AnalysisDirection dir = AnalysisDirection.FORWARD

  //transfer returns true if something changed
  abstract Set transfer(GraphNode node, Set input);
  abstract Set join(GraphNode node);

  abstract void store(GraphNode node, Set data);
  abstract Set load(GraphNode node);

  void analize(GraphNode startNode) {
    def worklist = new LinkedHashSet()
    eachNodeOf(startNode) { worklist << it }

    while (worklist.size() != 0) {
      def node = worklist.iterator().next()
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
      store(node, out)
    }
  }
}
