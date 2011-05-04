package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import decaf.optimizations.*

public class InterferenceGraph extends ColorableGraph { 
  LinkedHashSet<TempVar> variables;
  MethodDescriptor methodDesc;
  LivenessAnalysis la;
  LinkedHashMap regToInterferenceNode;

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

    dbgOut "2) Adding in register nodes to interference graph."
    AddInRegisterNodes()

    dbgOut "3) Setting up variables."
    SetupVariables()
    dbgOut "Variables (${variables.size()} in total):"
    //variables.each { v -> dbgOut "  $v" }
    dbgOut "Finished extracting variables."

    dbgOut "4) Computing the Interference Edges."
    ComputeInterferenceEdges()
    dbgOut "Finished computing interference edges, total number = ${edges.size()}"
    dbgOut "-----------"

    //DrawDotGraph();
    dbgOut "Finished building the interference graph."
  }

  void RunLivenessAnalysis() {
    la = new LivenessAnalysis();
    la.run(methodDesc.lowir)
  }

  void AddInRegisterNodes() {
    regToInterferenceNode = [:];

    Reg.eachReg { r -> 
      RegisterTempVar regToInject = r.GetRegisterTempVar()
      regToInterferenceNode[r.GetRegisterTempVar()] = new InterferenceNode(regToInject);
      assert regToInterferenceNode.keySet().contains(regToInject);
      AddNode(regToInterferenceNode[regToInject]); 
    }
  }

  void SetupVariables() {
    variables = new LinkedHashSet<TempVar>([])
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      node.anno['regalloc-liveness'].each { variables << it }
      if(node.getDef())
        variables << node.getDef();
      if(node.getUses() != null)
        node.getUses().each { tv -> variables << tv }
    }

    LinkedHashMap varToMovRelations

    // Now add an interference node for each variable (unless it's a registerTempVar)
    variables.each { v -> 
      if(!(v instanceof RegisterTempVar))
        AddNodeUnsafe(new InterferenceNode(v))
    }

    UpdateAfterNodesModified();
  }

  void ComputeInterferenceEdges() {
    edges = new LinkedHashSet()

    // Firs add the edges just based on the liveness analysis results.
    def varToLiveness = [:];
    variables.each { varToLiveness[it] = new LinkedHashSet() }

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      def liveVars = node.anno['regalloc-liveness']
      liveVars.each { lv -> 
        if(node.getDef() && node.getDef() != lv)
          varToLiveness[node.getDef()].add(lv)
        if(lv.type == TempVarType.PARAM) {
          liveVars.each { lv2 -> 
            if(lv2.type == TempVarType.PARAM)
              varToLiveness[lv] << lv2;
          }
        }
      }
    }

    varToLiveness.keySet().each { v -> 
      varToLiveness[v].remove(v);
      varToLiveness[v].each { lv -> 
        AddEdgeUnsafe(new InterferenceEdge(GetColoringNode(v), GetColoringNode(lv)));
      }
    }

    UpdateAfterEdgesModified();

    println "handled the liveness."

    LazyMap ColorNodeMustBe = new LazyMap({ new LinkedHashSet<InterferenceNode>() })
    LazyMap ColorsNodeCannotBe = new LazyMap({ new LinkedHashSet<InterferenceNode>() })

    variables.each { v -> 
      if(v.type == TempVarType.PARAM && v.id < 6)
        ColorNodeMustBe[GetColoringNode(v)] << Reg.getRegOfParamArgNum(v.id + 1);
    }

    // Now handle odd/node-specific cases.
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      BuildNodeToColoringNodeMap();

      // Add the interference edges.
      def liveVars = node.anno['regalloc-liveness']

      // Uncomment to see liveness analysis results.
      dbgOut "Node: $node, numLiveVars = ${liveVars.size()}"
      liveVars.each { dbgOut "  $it" }

      // Extra edges to add to handle special cases.
      switch(node) {
      case LowIrBinOp:
        // Handle modulo and division blocking.
        switch(node.op) {
        case BinOpType.DIV:
          ColorNodeMustBe[GetColoringNode(node.tmpVar)] << Reg.RAX;
          liveVars.each {
            if(it != node.tmpVar)
              ColorsNodeCannotBe[GetColoringNode(it)] << Reg.RAX
            ColorsNodeCannotBe[GetColoringNode(it)] << Reg.RDX;
          }
          break;
        case BinOpType.MOD:
          ColorNodeMustBe[GetColoringNode(node.tmpVar)] << Reg.RDX
          liveVars.each {
            if(it != node.tmpVar)
              ColorsNodeCannotBe[GetColoringNode(it)] << Reg.RDX;
            ColorsNodeCannotBe[GetColoringNode(it)] << Reg.RDX;
          }
          break;
        case BinOpType.LT:
        case BinOpType.LTE:
        case BinOpType.GT:
        case BinOpType.GTE:
        case BinOpType.EQ:
        case BinOpType.NEQ:
          // Not allowed to be r10 as that is used as a temporary.
          ColorsNodeCannotBe[GetColoringNode(node.getDef())] << Reg.R10;
          ColorsNodeCannotBe[GetColoringNode(node.leftTmpVar)] <<  Reg.R10;
          ColorsNodeCannotBe[GetColoringNode(node.rightTmpVar)] <<  Reg.R10;
          liveVars.each { lv -> 
            if(lv != node.getDef())
              ColorsNodeCannotBe[GetColoringNode(lv)] << Reg.R10;
          }
        }
        break;
      case LowIrMethodCall:
      case LowIrCallOut:
        node.paramTmpVars.eachWithIndex { ptv, i -> 
          if(i < 6) {
            ColorNodeMustBe[GetColoringNode(ptv)] << Reg.getRegOfParamArgNum(i + 1);
            liveVars.each { lv -> 
              def theParams = node.paramTmpVars.collect { it };
              if(lv != node.getDef() && !theParams.contains(lv))
                ColorsNodeCannotBe[GetColoringNode(lv)] << Reg.getRegOfParamArgNum(i+1);
            }
          }
        }
        // We also need to force the def-site to be RAX if the method isn't void.
        if(node instanceof LowIrCallOut || 
            node.descriptor.returnType == Type.INT || 
            node.descriptor.returnType == Type.BOOLEAN) {
          ColorNodeMustBe[GetColoringNode(node.tmpVar)] << Reg.RAX;
          liveVars.each { lv -> 
            if(lv != node.getDef())
              ColorsNodeCannotBe[GetColoringNode(lv)] << Reg.RAX;
          }
        }
        break;
      case LowIrReturn:
        // The following is really a preference not an absolute...
        /*if(methodDesc.returnType != Type.VOID) {
          //ForceNodeToColor(GetColoringNode(node.tmpVar), Reg.RAX);
          ColorNodeMustBe[GetColoringNode(node.tmpVar)] = Reg.RAX;
        }*/
        break;
      default:
        // Nothing else here at the time.
        break;
      }
    }

    println "finished traversing."

    ColorNodeMustBe.keySet().each { iNode ->
      ColorNodeMustBe[iNode].each { color -> 
        ForceNodeColor(iNode, color);      
      }
    }

    ColorsNodeCannotBe.keySet().each { iNode -> 
      ColorsNodeCannotBe[iNode].each { color -> 
        ForceNodeNotColor(iNode, color);
      }
    }

    UpdateAfterNodesModified();
    dbgOut "The number of interference edges is: ${edges.size()}"
    //edges.each { e -> dbgOut "$e" }
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

    if(a.isMovRelated() && b.isMovRelated()) {
      if(a.movRelatedNodes.contains(b.representative) &&
          b.movRelatedNodes.contains(a.representative)) {
        dbgOut "COALESCING!"
        //assert false; // <- cool!
        int numNewNeighbors = (neighborTable.GetNeighbors(a) + neighborTable.GetNeighbors(b)).size()
        return (numNewNeighbors < sigDeg());
      }
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

  public void ForceNodeColor(InterferenceNode nodeToForce, Reg color) {
    assert nodeToForce;
    assert color;

    Reg.eachReg { r -> 
      if(r != color) 
        AddEdgeUnsafe(new InterferenceEdge(nodeToForce, GetColoringNode(r.GetRegisterTempVar())));
    }

    UpdateAfterEdgesModified();
  }

  public void ForceNodeNotColor(InterferenceNode nodeToForce, Reg color) {
    AddEdge(new InterferenceEdge(nodeToForce, GetColoringNode(color.GetRegisterTempVar())));
  }

  ColoringNode GetColoringNode(def tv) {
    assert tv;
    assert tv instanceof TempVar;
    assert nodeToColoringNode;

    if(tv instanceof RegisterTempVar) {
      assert regToInterferenceNode
      assert regToInterferenceNode.keySet().contains(tv)
      return regToInterferenceNode[tv];
    }

    BuildNodeToColoringNodeMap();

    assert nodeToColoringNode.keySet().contains(tv);
    assert nodeToColoringNode[tv].nodes.contains(tv);
    return nodeToColoringNode[tv];
  }

  void AddMovRelation(TempVar src, TempVar dst) {
    assert src; assert dst;
    GetColoringNode(src).AddMovRelation(dst);
    GetColoringNode(dst).AddMovRelation(src);
  }

  void AddNodeWithPreExistingNeighbors(InterferenceNode iNode, LinkedHashSet<TempVar> neighbors) {
    Validate();
    assert !nodes.contains(iNode);

    BuildNodeToColoringNodeMap();
    LinkedHashSet<InterferenceNode> interferenceNeighbors = [];

    neighbors.each { n -> 
      //assert nodeToColoringNode.keySet().contains(n); 
      interferenceNeighbors << GetColoringNode(n)
    }

    // We need the set of coloring nodes that make up the neighbors.
    AddNode(iNode);
    interferenceNeighbors.each { AddEdgeUnsafe(new InterferenceEdge(iNode, it)) }
    UpdateAfterEdgesModified();

    Validate();
  }

  LinkedHashSet<InterferenceNode> GetNeighborsAndThenRemoveNode(InterferenceNode iNode) {
    Validate();
    LinkedHashSet<TempVar> neighbors = GetNeighbors(iNode);
    RemoveNode(iNode);
    Validate();
    return neighbors;
  }

  LinkedHashMap GetMultipleNeighborsAndThenRemoveNode(List<InterferenceNode> iNodes) {
    Validate();
    LinkedHashMap neighborMap = [:];
    iNodes.each { neighborMap[it] = GetNeighbors(iNode); }
    RemoveMultipleNodes(iNodes);
    Validate();
    return neighborMap;
  }

  public void Validate() {
    if(!DbgHelper.dbgValidationOn)
      return;
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
      color = Reg.getReg(tv.registerName);
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
    return "[INd, Rep = $representative]";//, clr = $color, mr = ${isMovRelated()}]"
  }

  public void Validate() {
    if(!DbgHelper.dbgValidationOn)
      return;
    assert representative;
    assert representative instanceof TempVar;
    assert nodes;
    nodes.each { assert it instanceof TempVar }
    assert nodes.contains(representative);
    assert movRelatedNodes != null;
    movRelatedNodes.each { assert it instanceof TempVar }
    assert (movRelatedNodes.intersect(nodes)).size() == 0;
    if(color != null) 
      assert color instanceof Reg
  }
}

public class InterferenceEdge extends ColoringEdge {
  public InterferenceEdge(ColoringNode a, ColoringNode b) {
    super(a, b)
  }

  public String toString() {
    return "[InterferenceEdge. nodes = $nodes]"
  }

  public void Validate() {
    if(!DbgHelper.dbgValidationOn)
      return;
    assert nodes; assert nodes.size() == 2;
    nodes.each { 
      it instanceof InterferenceNode;
      it.Validate();
    }
    if(N1().color && N2().color)
      assert N1().color != N2().color;
    PerformSymmetric { cn1, cn2 -> 
      cn1.movRelatedNodes.each { mrn -> 
        if(mrn != cn2.representative)
          assert cn2.nodes.contains(mrn) == false;
      }
    }
  }
}
