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
      variables += new LinkedHashSet<TempVar>(node.anno['regalloc-liveness'])
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

    def n2CN = BuildNodeToColoringNodeMap();

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      // Add the interference edges.
      def liveVars = node.anno['regalloc-liveness']

      if(node.getDef()) {
        liveVars.each { v -> 
          assert n2CN[(v)];
          if(node.getDef() != v)
            addEdge(new InterferenceEdge(n2CN[(v)], n2CN[(node.getDef())]))
        }
      }

      // Now we need to handle modulo and division blocking.
      if(node instanceof LowIrBinOp) {
        RegisterTempVar blockedReg;

        if(node.op == BinOpType.DIV)
          blockedReg = registerNodes.find { rn -> rn.color == 'rdx' }
        else if(node.op == BinOpType.MOD)
          blockedReg = registerNodes.find { rn -> rn.color == 'rax' }
        assert blockedReg;
        liveVars.each { addEdge(new InterferenceEdge(blockedReg, n2CN[(it)])) }
      }

      // Finally we need to handle method calls and callouts.
      if(node instanceof LowIrMethodCall || node instanceof LowIrCallOut) {
        // We have to color the first parameters as follows:
        def argNumToColor = 
          [1 : 'rdi', 
           2 : 'rsi', 
           3 : 'rdx',
           4 : 'rcx',
           5 : 'r8',
           6 : 'r9'] 
        
        node.paramTmpVars.eachWithIndex { ptv, i -> 
          assert n2CN[(ptv)];
          if(i < 6)            
            ForceNodeColor(n2CN[(ptv)], argNumToColor[(i + 1)]);
        }
      }
    }

    UpdateAfterEdgesModified();
    dbgOut "Finished computing interference edges, total number = ${edges.size()}"
  }

  int sigDeg() {
    return 14;
  }

  boolean isSigDeg(InterferenceNode node) {
    return node.getDegree() >= sigDeg();
  }

  boolean CanCoalesceNodes(InterferenceNode a, InterferenceNode b) {
    assert a; assert b;

    if(!a.isMovRelated() || !b.isMovRelated())
      return false;
    assert a.movRelatedNodes.contains(b.representative)
    assert a.movRelatedNodes.contains(a.representative)

    return (neighborTable.GetNeighbors(a) + neighborTable.GetNeighbors(b)).size() < sigDeg()
  }

  void CoalesceNodes(InterferenceNode a, InterferenceNode b) {
    assert a; assert b;
    assert nodes.contains(a) && nodes.contains(b)
    assert CanCoalesceNodes(a, b);

    InterferenceNode c = a.CoalesceWith(b);
    nodes.removeNodes([a, b]);    
    addNode(c);

    nodes.each { n -> 
      if(n.movRelatedNodes.contains(a) || n.movRelatedNodes.contains(b)) {
        n.RemoveMovRelation(a);
        n.RemoveMovRelation(b);
        n.AddMovRelation(c);
      }
    }

    // Now we have to make sure to have transferred the edges.
    def edgesToAdd = []
    def needToUpdate = { curNode -> curNode == a || curNode == b }
    edges.each { e -> 
      if(needToUpdate(e.cn1) || needToUpdate(e.cn2)) {
        InterferenceEdge updatedEdge = new InterferenceEdge(e.cn1, e.cn2)
        updatedEdge.cn1 = needToUpdate(e.cn1) ? c : e.cn1
        updatedEdge.cn2 = needToUpdate(e.cn2) ? c : e.cn2
        edgesToAdd += [updatedEdge]
      }
    }

    edgesToAdd.each { addEdge(it) }
    UpdateAfterNodesModified();
  }

  public void ForceNodeColor(InterferenceNode nodeToForce, String color) {
    registerNodes.each { rn -> 
      if(rn.color != color)
        addEdge(new InterferenceEdge(nodeToForce, n));
    }

    UpdateAfterEdgesModified();
  }

  public void ForceNodeNotColor(InterferenceNode nodeToForce, String color) {
    registerNodes.each { rn -> 
      if(rn.color == color) {
        addEdge(new InterferenceEdge(nodeToForce, n));
        UpdateAfterEdgesModified();
        return;
      }
    }
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
    def allRepresentedNodes = []
    nodes.each { allRepresentedNodes += (it.nodes as List) }
    assert (allRepresentedNodes.size() == new LinkedHashSet(allRepresentedNodes))
  }
}
