package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class InterferenceGraph {
  LinkedHashSet<TempVar> variables;
  MethodDescriptor methodDesc;
  LivenessAnalysis la;
  def InterferenceEdges;
  
  public InterferenceGraph(MethodDescriptor md) {
    assert(md)
    methodDesc = md
  }
  
  void CalculateInterferenceGraph() {
    la = new LivenessAnalysis(methodDesc);
    la.RunLivenessAnalysis()
    
    variables = la.variables
    InterferenceEdges = new LinkedHashSet()
    
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(la.liveOut[(node)] && node.getDef()) 
        la.liveOut[(node)].each { v -> 
          if(v != node.getDef())
            AddInterferenceEdge(node.getDef(), v)
        }
    }
    
    println "The Interference Edges are: "
    InterferenceEdges.each { println "  $it" }
    
    println "The variables of significant degree for k = 8 are:"
    (getSignificantDegreeNodes(8)).each { node -> 
      println " $node"
    }
  }
  
  void AddInterferenceEdge(TempVar v1, TempVar v2) {
    assert(variables.contains(v1))
    assert(variables.contains(v2))
    assert(v1)
    assert(v2)
    
    // We should only ever be adding this edge once.
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
}
