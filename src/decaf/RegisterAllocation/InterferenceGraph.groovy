package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import decaf.optimizations.*

public class InterferenceGraph extends ColorableGraph { 
  LinkedHashSet<TempVar> variables;
  MethodDescriptor methodDesc;
  LivenessAnalysis la;
  LinkedHashSet<RegisterTempVar> registerNodes;

  public InterferenceGraph(MethodDescriptor md) {
    super()
    assert(md)
    methodDesc = md
    CalculateInterferenceGraph()
  }

  void CalculateInterferenceGraph() {
    dbgOut "Now building the interference graph for method: ${methodDesc.name}"
    // Make sure results from previous liveness analysis don't interfere
    Traverser.eachNodeOf(methodDesc.lowir) { node -> node.anno.remove('regalloc-liveness') }

    dbgOut "1) Running Liveness Analysis."
    RunLivenessAnalysis()

    dbgOut "2) Setting up variables."
    SetupVariables()
    dbgOut "Variables = $variables"
    dbgOut "Finished extracting variables. Number of variables = ${variables.size()}"

    dbgOut "3) Computing the Interference Edges."
    ComputeInterferenceEdges()
    dbgOut "Finished computing interference edges, total number = ${edges.size()}"
    edges.each { dbgOut it }
    dbgOut "-----------"

    //DrawDotGraph();
    dbgOut "Finished building the interference graph."
  }

  void RunLivenessAnalysis() {
    la = new LivenessAnalysis();
    la.run(methodDesc.lowir)
  }

  void SetupVariables() {
    variables = new LinkedHashSet<TempVar>([])
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      node.anno['regalloc-liveness'].each { variables << it }
    }

    // Now add an interference node for each variable (unless it's a registerTempVar)
    variables.each { v -> 
      if(!(v instanceof RegisterTempVar))
        AddNode(new InterferenceNode(v))
    }
  }

  void ComputeInterferenceEdges() {
    edges = new LinkedHashSet()

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      BuildNodeToColoringNodeMap();
      // Add the interference edges.
      def liveVars = node.anno['regalloc-liveness']

      if(node.getDef()) {
        liveVars.each { v -> 
          if(node.getDef() != v)
            AddEdge(new InterferenceEdge(GetColoringNode(v), GetColoringNode(node.getDef())))
        }
      }

      // Now we need to handle modulo and division blocking.
      switch(node) {
      case LowIrBinOp:
        if(node.op == BinOpType.DIV || node.op == BinOpType.MOD)
          liveVars.each { 
            AddEdge(new InterferenceEdge(RegColor.RDX.getRetTempVar(), GetColoringNode(it))) 
            AddEdge(new InterferenceEdge(RegColor.RAX.getRetTempVar(), GetColoringNode(it))) 
          }
        break;
      case LowIrMethodCall:
      case LowIrCallOut:
        node.paramTmpVars.eachWithIndex { ptv, i -> 
          if(i < 6) 
            ForceNodeColor(GetColoringNode(ptv), RegColor.getRegOfArgNum(i + 1));
        }
        break;
      case LowIrReturn:
        if(methodDesc.returnType != Type.VOID)
          ForceNodeToColor(GetColoringNode(node.tmpVar), RegColor.RAX);
        break;
      default:
        // Nothing else here at the time.
        break;
      }
    }

    UpdateAfterEdgesModified();
  }

  int sigDeg() {
    return 14; // We aren't coloring with rsp and rbp.
  }

  boolean isSigDeg(InterferenceNode node) {
    assert node;
    return neighborTable.GetDegree(node) >= sigDeg();
  }

  boolean CanCoalesceNodes(InterferenceNode a, InterferenceNode b) {
    assert a; assert b;
    a.nodes.each { node -> assert !(node instanceof RegisterTempVar) }
    b.nodes.each { node -> assert !(node instanceof RegisterTempVar) }

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
    AddNode(c);

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

    edgesToAdd.each { AddEdge(it) }

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
    assert nodeToForce;
    assert color;

    RegColor.eachRegNode { rn -> 
      if(rn != color) 
        AddEdge(new InterferenceEdge(nodeToForce, n));
    }
  }

  public void ForceNodeNotColor(InterferenceNode nodeToForce, RegColor color) {
    AddEdge(new InterferenceEdge(nodeToForce, color));
  }

  ColoringNode GetColoringNode(TempVar tv) {
    assert tv;
    assert nodeToColoringNode;
    assert nodeToColoringNode.keySet().contains(tv);
    assert nodeToColoringNode[tv].nodes.contains(tv);
    return nodeToColoringNode[tv];
  }

  void AddNodeWithPreExistingNeighbors(InterferenceNode iNode, LinkedHashSet<TempVar> neighbors) {
    Validate();
    assert !nodes.contains(iNode);

    BuildNodeToColoringNodeMap();
    LinkedHashSet<InterferenceNode> interferenceNeighbors = [];

    neighbors.each { n -> 
      assert nodeToColoringNode.keySet().contains(n); 
      interferenceNeighbors << GetColoringNode(n)
    }

    // We need the set of coloring nodes that make up the neighbors.
    println "adding node. ${nodes.size()}"
    AddNode(iNode);
    println "ok, ${nodes.size()}"
    interferenceNeighbors.each { addEdge(new InterferenceEdge(iNode, it)) }

    Validate();
  }

  LinkedHashSet<TempVar> GetNeighborsAndThenRemoveNode(InterferenceNode iNode) {
    Validate();
    LinkedHashSet<TempVar> neighbors = GetNeighbors(iNode);
    RemoveNode(iNode);
    Validate();
    return neighbors;
  }

  public void Validate() {
    assert nodes != null; assert edges != null;
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
    nodes.each { node -> node.nodes.each { allRepresentedNodes << it } }
    assert (allRepresentedNodes.size() == (new LinkedHashSet(allRepresentedNodes)).size())
  }
}

class InterferenceNode extends ColoringNode {
  LinkedHashSet movRelatedNodes;
  private boolean frozen = false;
  
  public InterferenceNode() { 
    assert false;
  }

  public InterferenceNode(TempVar tv) {
    super(tv);
    if(tv instanceof RegisterTempVar) {
      assert tv.registerName;
      color = RegColor.getReg(tv.registerName);
    }

    movRelatedNodes = new LinkedHashSet();
  }

  public InterferenceNode ResultOfCoalescingWith(InterferenceNode b) {
    a.Validate(); b.Validate();
    assert color != b.color;

    InterferenceNode c = new InterferenceNode(representative);
    c.nodes = nodes + b.nodes
    c.movRelatedNodes = movRelatedNodes + b.movRelatedNodes
    c.UpdateMoveRelatedNodes();
    c.color = (color != null) ? color : b.color;
    c.Validate();
    return c;
  }

  boolean isMovRelated() {
    assert movRelatedNodes != null;
    return (frozen ? false : (movRelatedNodes.size() > 0))
  }

  void UpdateMoveRelatedNodes() {
    assert nodes
    nodes.each { movRelatedNodes.remove(it) }
  }

  void AddMovRelation(TempVar n) {
    assert n;
    movRelatedNodes << n;
    UpdateMoveRelatedNodes();
  }

  void RemoveMovRelation(TempVar n) {
    assert n; assert movRelatedNodes.contains(n);
    movRelatedNodes.remove(n);
    UpdateMoveRelatedNodes();
  }

  void Freeze() {
    assert !frozen;
    frozen = true;
    assert isMovRelated() == false;
  }

  public String toString() {
    return "[InterferenceNode. Rep = $representative, color = $color, frozen = $frozen]"
  }

  public void Validate() {
    assert representative;
    assert representative instanceof TempVar;
    assert nodes;
    nodes.each { assert it instanceof TempVar }
    assert nodes.contains(representative);
    assert movRelatedNodes != null;
    movRelatedNodes.each { assert it instanceof TempVar }
    assert (movRelatedNodes.intersect(nodes)).size() == 0;
    if(color != null) 
      assert color instanceof RegColor
  }
}

class InterferenceEdge extends ColoringEdge {
  public InterferenceEdge(ColoringNode a, ColoringNode b) {
    super(a, b)
  }

  public String toString() {
    return "[InterferenceEdge. cn1 = $cn1, cn2 = $cn2]"
  }

  public void Validate() {
    assert cn1; assert cn2;
    assert cn1 instanceof InterferenceNode;
    assert cn2 instanceof InterferenceNode;
    cn1.Validate();
    cn2.Validate();
    if(cn1.color && cn2.color)
      assert cn1.color != cn2.color;
    cn1.movRelatedNodes.each { mrn -> 
      if(mrn != cn2.representative)
        assert cn2.nodes.contains(mrn) == false;
    }
    cn2.movRelatedNodes.each { mrn -> 
      if(mrn != cn1.representative)
        assert cn1.nodes.contains(mrn) == false;
    }
  }
}
