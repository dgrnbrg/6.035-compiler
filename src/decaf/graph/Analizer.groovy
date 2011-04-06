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

  abstract void store(GraphNode key, Set data);
  abstract Set load(GraphNode key);

  def worklistInit = { startNode ->
    def worklist = new LinkedHashSet()
    eachNodeOf(startNode) { worklist << it }
    return worklist
  }

  void analize(GraphNode startNode) {
    def worklist = worklistInit(startNode)

    while (worklist.size() != 0) {
      def node = worklist.iterator().next()
      worklist.remove(node)
      def old = load(node).clone()
      def input = join(node)
if (node instanceof decaf.optimizations.LCMEdge && node.fst == node.snd) println 'wtf mate?!?!?'
      def out = transfer(node, input)
if (node instanceof decaf.LowIrNode && node.label == 'label10') {
//  println "adding node12's preds back to list, current antIn = $out"
//  println "$node\n  old: $old\n  in: $input\n  out: $out"
}
      if (old != out) {
//println "propagating"
      store(node, out)
        if (dir == AnalysisDirection.FORWARD) {
          worklist.addAll(node.getSuccessors())
        } else if (dir == AnalysisDirection.BACKWARD) {
if (node.label == 'node12') println "adding node12's preds back to list, current antIn = $out"
          worklist.addAll(node.getPredecessors())
        } else {
          assert false
        }
      }
    }
  }
}
