package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import decaf.optimizations.*

class InterferenceGraph {
  LinkedHashSet<TempVar> variables;
  MethodDescriptor methodDesc;
  LivenessAnalysis la;
  def InterferenceEdges;
  def NodeToColor;
  def neighborTable;

  public InterferenceGraph(MethodDescriptor md) {
    assert(md)
    methodDesc = md
  }
  
  void CalculateInterferenceGraph() {
    // Make sure results from previous liveness analysis don't interfere
    Traverser.eachNodeOf(methodDesc.lowir) { node -> node.anno.remove('regalloc-liveness') }

    la = new LivenessAnalysis()
    la.run(methodDesc.lowir)
    
    variables = new LinkedHashSet<TempVar>()

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      variables += new LinkedHashSet<TempVar>(node.anno['regalloc-liveness'])
      //println "Liveness Result for Node: $node"
      //println node.anno['regalloc-liveness']
    }

    println "Finished running liveness analysis."

    InterferenceEdges = new LinkedHashSet()
    
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      // Add the interference edges.
      def liveVars = node.anno['regalloc-liveness']
      //println "calculating interference edges for node: $node"
      println "number of liveout = ${liveVars.size()}"
      liveVars.eachWithIndex { v1, i1 -> 
        liveVars.eachWithIndex { v2, i2 -> 
          if(i1 < i2 && v1 != v2) 
            AddInterferenceEdge(v1, v2)
        }
      }
    }
    
    println "The Interference Edges are: "
    InterferenceEdges.each { println "  $it" }
    
    println "The variables of significant degree for k = 8 are:"
    //(getSignificantDegreeNodes(8)).each { node -> 
    //  println " $node"
    //}

    DrawDotGraph();
  }
  
  void AddInterferenceEdge(TempVar v1, TempVar v2) {
    assert(v1)
    assert(v2)
    //assert(variables.contains(v1))
    //assert(variables.contains(v2))

    def newEdge = new LinkedHashSet<TempVar>([v1, v2])
    
    assert(InterferenceEdges != null)
    InterferenceEdges += new LinkedHashSet([newEdge])
  }
  
  LinkedHashSet<TempVar> getNeighbors(TempVar v) {
    assert(variables.contains(v))
    LinkedHashSet<TempVar> answer = new LinkedHashSet<TempVar>()
    
    InterferenceEdges.each { edge -> 
      if(edge.contains(v)) {
        LinkedHashSet<TempVar> neighbor = edge - new LinkedHashSet<TempVar>([v])
        answer += neighbor
      }
    }
    
    return answer
  }
  
  LinkedHashSet<TempVar> getSignificantDegreeNodes(int sigDeg) {
    LinkedHashSet<TempVar> answer = new LinkedHashSet<TempVar>()
    
    variables.each { v -> 
      def neighbors = getNeighbors(v)
      if(neighbors.size() >= sigDeg)
        answer += new LinkedHashSet<TempVar>([v])
    }
    
    return answer;
  }

  void BuildNeighborTable() {
    neighborTable = [:]
    variables.each { v -> neighborTable[(v)] = new LinkedHashSet() }

    assert InterferenceEdges != null
    InterferenceEdges.each { edge -> 
      def nodes = edge.collect { it }
      neighborTable[(nodes[0])] += new LinkedHashSet([nodes[1]])
      neighborTable[(nodes[1])] += new LinkedHashSet([nodes[0]])
    }
  }

  void ColorGraph(int degree) {
    // assuming that there are no spills. Do this by showing that every 
    // variable has less edges than degree.
    NodeToColor = [:]
    
    BuildNeighborTable();

    variables.each { v -> 
      assert neighborTable[(v)].size() < degree
    }

    // note that we are not considering the base pointer and stack pointer.
    def colors = ['rax', 'rbx', 'rcx', 'rdx', 
              'rsi', 'rdi', 
              'r8', 'r9', 'r10', 'r11', 
              'r12', 'r13', 'r14', 'r15']

    
  }

  void DrawDotGraph() {
    def graphFile = "${methodDesc.name}", extension = 'pdf'
    graphFile = graphFile + '.' + 'InterferenceGraph' + '.' + extension
    println "Writing interference graph output to $graphFile"
    assert graphFile

    def dotCommand = "dot -T$extension -o $graphFile"

    Process dot = dotCommand.execute()
    def dotOut = new PrintStream(dot.outputStream)
    //dot.consumeProcessErrorStream(System.err)

    def varToLabel = { "TVz${it.id}z${it.type}" }
    println 'digraph g {'
    dotOut.println 'digraph g {'
    InterferenceEdges.each { edge -> 
      def pairOfVariables = edge.collect { it }
      assert pairOfVariables.size() == 2      
      def v1 = pairOfVariables[0], v2 = pairOfVariables[1]
      println "${varToLabel(v1)} -> ${varToLabel(v2)}"
      dotOut.println "${varToLabel(v1)} -> ${varToLabel(v2)}"
    }
    println '}'
    dotOut.println '}'
    dotOut.close()
  }
}
