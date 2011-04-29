package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import decaf.optimizations.*

class InterferenceGraph extends ColorableGraph { 
  LinkedHashSet<TempVar> variables;
  MethodDescriptor methodDesc;
  LivenessAnalysis la;
  LinkedHashSet<RegisterTempVar> registerNodes;

  public InterferenceGraph(MethodDescriptor md) {
    assert(md)
    methodDesc = md
    neighborTable = new NeighborTable()
    CalculateInterferenceGraph()
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
      node.anno['regalloc-liveness'].each { variables << it }
    }

    // Now add an interference node for each variable (unless it's a registerTempVar)
    variables.each { v -> 
      if(!(v instanceof RegisterTempVar))
        addNode(new InterferenceNode(v))
    }

    dbgOut "Finished extracting variables. Number of variables = ${variables.size()}"
  }

  void ComputeInterferenceEdges() {
    dbgOut "Now computing interference edges."
    edges = new LinkedHashSet()

    BuildNodeToColoringNodeMap();

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      // Add the interference edges.
      def liveVars = node.anno['regalloc-liveness']

      if(node.getDef()) {
        liveVars.each { v -> 
          if(node.getDef() != v)
            addEdge(new InterferenceEdge(GetColoringNode(v), GetColoringNode(node.getDef())))
        }
      }

      // Now we need to handle modulo and division blocking.
      switch(node) {
      case LowIrBinOp:
        if(node.op == BinOpType.DIV || node.op == BinOpType.MOD)
          liveVars.each { 
            addEdge(new InterferenceEdge(RegColor.RDX.getRetTempVar(), GetColoringNode(it))) 
            addEdge(new InterferenceEdge(RegColor.RAX.getRetTempVar(), GetColoringNode(it))) 
          }
        break;
      case LowIrMethodCall:
      case LowIrCallOut:  
        node.paramTmpVars.eachWithIndex { ptv, i -> 
          if(i < 6) ForceNodeColor(GetColoringNode(ptv), RegColor.getRegOfArgNum(i + 1));
        }
        break;
      default:
        // Nothing else here at the time.
        break;
      }

      // We need to force the return use to be colored 'rax'
      assert false; // yet to be implemented.
      
    }

    UpdateAfterEdgesModified();
    dbgOut "Finished computing interference edges, total number = ${edges.size()}"
  }

  int sigDeg() {
    return 14; // We aren't coloring with rsp and rbp.
  }

  boolean isSigDeg(InterferenceNode node) {
    assert node;
    return node.getDegree() >= sigDeg();
  }

  boolean CanCoalesceNodes(InterferenceNode a, InterferenceNode b) {
    assert a; assert b;

    if(a.isMovRelated() || b.isMovRelated()) {
      assert a.movRelatedNodes.contains(b.representative)
      assert a.movRelatedNodes.contains(a.representative)
      int numNewNeighbors = (neighborTable.GetNeighbors(a) + neighborTable.GetNeighbors(b)).size()
      return (numNewNeighbors < sigDeg());
    }

    return false;
  }

  void CoalesceNodes(InterferenceNode a, InterferenceNode b) {
    assert a; assert b;
    assert nodes.contains(a) && nodes.contains(b)
    assert CanCoalesceNodes(a, b);

    InterferenceNode c = a.CoalesceWith(b);
    addNode(c);

    // Now we have to make sure to have transferred the edges.
    List<InterferenceEdge> edgesToAdd = []
    def needToUpdate = { curNode -> curNode == a || curNode == b }
    edges.each { e -> 
      if(needToUpdate(e.cn1) || needToUpdate(e.cn2)) {
        InterferenceEdge updatedEdge = new InterferenceEdge(e.cn1, e.cn2);
        updatedEdge.cn1 = needToUpdate(e.cn1) ? c : e.cn1;
        updatedEdge.cn2 = needToUpdate(e.cn2) ? c : e.cn2;
        updatedEdge.Validate();
        edgesToAdd << updatedEdge;
      }
    }

    edgesToAdd.each { addEdge(it) }

    nodes.removeNode(a);
    nodes.removeNode(b);    

    nodes.each { n -> 
      if(n.movRelatedNodes.contains(a) || n.movRelatedNodes.contains(b)) {
        n.RemoveMovRelation(a);
        n.RemoveMovRelation(b);
        n.AddMovRelation(c);
      }
    }

    
  }

  public void ForceNodeColor(InterferenceNode nodeToForce, RegColor color) {
    RegColor.eachRegNode { rn -> 
      if(rn != color) addEdge(new InterferenceEdge(nodeToForce, n));
    }
  }

  public void ForceNodeNotColor(InterferenceNode nodeToForce, RegColor color) {
    addEdge(new InterferenceEdge(nodeToForce, color));
  }

  InterferenceNode GetColoringNode(TempVar tv) {
    assert tv;
    assert nodeToColoringNode;
    assert nodeToColoringMap.keySet().contains(tv);
    assert nodeToColoringMap[node].nodes.contains(tv);
    return nodeToColoringMap[node];
  }

  public void Validate() {
    assert nodes; assert edges;
    assert neighborTable;
    assert methodDesc;
    assert variables;
    nodes.each { 
      assert it instanceof InterferenceNode;
      it.Validate();
    }
    edges.each {
      assert it instanceof InterferenceEdge;
      it.Validate();
    }
    variables.each { assert it instanceof TempVar; }

    // now verify there are no duplicates between tempvars
    List<InterferenceNode> allRepresentedNodes = []
    nodes.each { allRepresentedNodes += (it.nodes as List) }
    assert (allRepresentedNodes.size() == new LinkedHashSet(allRepresentedNodes))

    assert nodeToColoringNode.keySet() == variables;
    BuildNodeToColoringNodeMap();
    assert nodeToColoringNode.keySet() == variables;
  }
}
