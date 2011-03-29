package decaf.optimizations
import decaf.*

class CopyPropagation {
  int iteration = 0
  def propagate(LowIrNode startNode) {
    //for each node (worklist algorithm)
    def oldTag = "copy-propagation-$iteration".toString()
    iteration++
    def newTag = "copy-propagation-$iteration".toString()

    def unvisitedNodes = [startNode]
    while (unvisitedNodes.size() != 0) {
      def node = unvisitedNodes.pop()
      //mark and add unvisited to list
      node.anno.remove(oldTag)
      node.anno[newTag] = true
      node.successors.each {
        if (!(it.anno[newTag])) {
          unvisitedNodes << it
        }
      }

      if (node instanceof LowIrMov) {
        def u = new HashSet(node.getDef().useSites)
        def results = u*.replaceUse(node.dst, node.src)
        assert results.every{ it > 0 }
      }
    }
  }
}
