package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class LoopAnalizer {
  def domComps = new DominanceComputations()
  def loops = []

  def run(startNode) {
    domComps.computeDominators(startNode)
    eachNodeOf(startNode) { node ->
      if (node.metaText == 'landing pad') {
        if (node.anno['loop-contains-breaks']) return //can't do anything, it's not simple
        def exits = [node.anno['loop-exit-node']] + node.anno['loop-contains-breaks']
        def header = node
        while (header && !header.predecessors.any{domComps.dominates(header, it)}) {
          assert header.successors.size() == 1
          header = header.successors[0]
        }
        assert header.predecessors.size() == 2
        //now we've found the loop header, and we already know the exit node
        //so we'll try to enumerate the body
        def visited = new LinkedHashSet()
        def toVisit = new LinkedHashSet([header])
        while (toVisit.size() != 0) {
          def cur = toVisit.iterator().next()
          toVisit.remove(cur)
          visited << cur
          if (!(cur in exits)) {
            toVisit.addAll(cur.successors - visited)
          }
        }
        //ensure that we get the nodes between the exit node and header
        //these should only form a basic block
        def missing = header.predecessors.find{domComps.dominates(header, it)}
        assert missing != null
        while (!(missing in visited)) {
          visited << missing
          assert missing.predecessors.size() == 1
          missing = missing.predecessors[0]
        }
        loops << new Loop(header: header, exits: exits, body: visited)
      }
    }
  }

  static def copyLoop(Loop loopIn, tempFactory) {
    def copier = new Copier(tempFactory)
    def loopOut = new Loop(
      header: copier.nodeCopyMap[loopIn.header],
      exits: loopIn.exits.collect{copier.nodeCopyMap[it]}
    )
    loopOut.body = copier.copy(loopIn.body)
    return [loopOut, copier.tmpCopyMap, copier.nodeCopyMap]
  }
}

class Loop {
  LowIrNode header
  def exits //collection of exits
  Collection body

  String toString() { "Loop(start: ${header?.label}, end: ${exits*.label})" }
}
