package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class DeadStoreElimination extends Analizer {
  DeadStoreElimination() {
    dir = AnalysisDirection.BACKWARD
  }

  def map = new LazyMap({new HashSet()})

  void store(GraphNode node, Set data) {
    map[node] = data
  }

  Set load(GraphNode node) {
    return map[node]
  }

  def gen(node) {
    if (node instanceof LowIrStore) {
      return Collections.singleton(new MemoryLocation(desc: node.desc, index: node.index))
    } else {
      return new HashSet()
    }
  }

  def kill(node) {
    if (node instanceof LowIrLoad) {
      return Collections.singleton(new MemoryLocation(desc: node.desc, index: node.index))
    } else {
      return new HashSet()
    }
  } 

  Set transfer(GraphNode node, Set input) {
    input.removeAll(kill(node))
    input.addAll(gen(node))
    return input
  }

  Set join(GraphNode node) {
    return node.successors ? node.successors.inject(load(node.successors[0]).clone()) { set, succ ->
       set.retainAll(load(succ)); set
    } : new HashSet()
  }

  def run(startNode) {
    //init the worklist in reverse to allow the propagator to visit the nodes
    worklistInit = { k ->
      def worklist = []
      eachNodeOf(k) { worklist << it }
      def worklistFinal = new LinkedHashSet()
      for (int i = worklist.size()-1; i >= 0; i--) {
        worklistFinal << worklist[i]
      }
      return worklistFinal
    }
    analize(startNode)
    def worklist = new LinkedHashSet()
    eachNodeOf(startNode) {
      //don't delete the first node
      if (it.is(startNode)) return
      //if the defined variable isn't live, delete this node
      if (it instanceof LowIrStore && new MemoryLocation(desc: it.desc, index: it.index) in 
        join(it)) {
        it.excise()
      }
    }
  }
}

class MemoryLocation {
  VariableDescriptor desc
  TempVar index

  int hashCode() {
    if (index == null) {
      return desc.hashCode() * 37
    } else {
      return desc.hashCode() * 37 + index.hashCode() * 97
    }
  }

  boolean equals(Object other) {
    return other != null && other.getClass() == MemoryLocation.class && other.hashCode() == this.hashCode()
  }

  String toString() {
    return index ? "[$desc @ $index]" : "[$desc]" 
  }
}
