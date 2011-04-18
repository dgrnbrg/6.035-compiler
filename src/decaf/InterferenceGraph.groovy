package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class InterferenceGraph {
  LinkedHashSet<TempVar> variables;
  MethodDescriptor methodDesc;
  LivenessAnalysis la;
  def Edges;
  
  public InterferenceGraph(MethodDescriptor md) {
    assert(md)
    methodDesc = md
  }
  
  void CalculateInterferenceGraph() {
    la = new LivenessAnalysis(methodDesc);
    la.RunLivenessAnalysis()
    
    variables = la.variables
    Edges = new LinkedHashSet()
    
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(la.liveOut[(node)] && node.getDef()) 
        la.liveOut[(node)].each { v -> 
          if(v != node.getDef())
            AddInterferenceEdge(node.getDef(), v)
        }
    }
    
    println "The Interference Edges are: "
    Edges.each { println "  $it" }
  }
  
  void AddInterferenceEdge(TempVar v1, TempVar v2) {
    assert(variables.contains(v1))
    assert(variables.contains(v2))
    assert(v1)
    assert(v2)
    
    // We should only ever be adding this edge once.
    def newEdge = new LinkedHashSet<TempVar>([v1, v2])
    
    assert(Edges != null)
    Edges += new LinkedHashSet([newEdge])
  }
}
