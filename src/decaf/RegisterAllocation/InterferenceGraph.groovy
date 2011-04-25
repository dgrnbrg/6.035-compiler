package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import decaf.optimizations.*

class InterferenceEdge extends ColoringEdge {
  public InterferenceEdge(ColoringNode a, ColoringNode b) {
    super(a, b)
  }
}

class InterferenceGraph extends ColorableGraph { 
  LinkedHashSet<TempVar> variables;
  MethodDescriptor methodDesc;
  LivenessAnalysis la;

  public InterferenceGraph(MethodDescriptor md) {
    assert(md)
    methodDesc = md
    neighborTable = new NeighborTable()
    ComputeInterferenceEdges()
  }

  void RunLivenessAnalysis() {
    dbgOut "Running Liveness Analysis on method: ${methodDesc.name}"
    la = new LivenessAnalysis();
    la.run(methodDesc.lowir)
    dbgOut "Finished Running Liveness Analysis."
  }

  void SetupVariables() {
    dbgOut "Now extracting all variables from method: ${methodDesc.name}"
    variables = new LinkedHashSet<TempVar>()
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      variables += new LinkedHashSet<TempVar>(node.anno['regalloc-liveness'])
    }
    dbgOut "Finished extracting variables. Number of variables = ${variables.size()}"
  }

  void ComputeInterferenceEdges() {
    dbgOut "Now computing interference edges."
    edges = new LinkedHashSet()

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      // Add the interference edges.
      def liveVars = node.anno['regalloc-liveness']
      liveVars.eachWithIndex { v1, i1 -> 
        liveVars.eachWithIndex { v2, i2 -> 
          if(i1 < i2 && v1 != v2) 
            addEdge(new InterferenceEdge(v1, v2))
        }
      }
    }

    UpdateAfterEdgesModified();
    dbgOut "Finished computing interference edges, total number = ${edges.size()}"
  }

  void CalculateInterferenceGraph() {
    dbgOut "Now building the interference graph for method: ${methodDesc.name}"
    // Make sure results from previous liveness analysis don't interfere
    Traverser.eachNodeOf(methodDesc.lowir) { node -> node.anno.remove('regalloc-liveness') }

    RunLivenessAnalysis()
    SetupVariables()
    ComputeInterferenceEdges()

    //DrawDotGraph();
    dbgOut "Finished building the interference graph."
  }

  /*LinkedHashSet<TempVar> getSignificantDegreeNodes(int sigDeg) {
    LinkedHashSet
  }*/
}
