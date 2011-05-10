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
      if (node instanceof LowIrIntLiteral && node.useless) return //skip useless lowirintliterals
      node.anno['regalloc-liveness'].each { variables << it }
      if(node.getDef())
        variables << node.getDef();
      if(node.getUses() != null)
        node.getUses().each { tv -> variables << tv }
    }

    // Now add an interference node for each variable (unless it's a registerTempVar)
    variables.each { v -> 
      if(!(v instanceof RegisterTempVar))
        AddNodeUnsafe(new InterferenceNode(v))
    }

    UpdateAfterNodesModified();
  }

  void ComputeInterferenceEdges() {
    edges = new LinkedHashSet()

def t0
def prof = { if (t0 == null) t0 = System.currentTimeMillis() else {println "profiler -- $it: ${System.currentTimeMillis()-t0}ms"; t0 = null } }

    def varToLiveness = [:];
    variables.each { varToLiveness[it] = new LinkedHashSet() }

prof()
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      def liveVars = node.anno['regalloc-liveness']
      liveVars.each { lv -> 
        liveVars.each { lv2 ->
          varToLiveness[lv] << lv2;
        }
      }
    }
prof('computing cross edges')

prof()
    BuildNodeToColoringNodeMap();
    varToLiveness.keySet().each { v -> 
      varToLiveness[v].remove(v);
      varToLiveness[v].each { lv -> 
        AddEdgeUnsafe(new InterferenceEdge(GetColoringNodeUnsafe(v), GetColoringNodeUnsafe(lv)));
      }
    }
prof('adding edges into graph')

prof()
    UpdateAfterEdgesModified();
prof('update after edges modified')

    LazyMap ColorNodeMustBe = new LazyMap({ new LinkedHashSet<InterferenceNode>() })
    LazyMap ColorsNodeCannotBe = new LazyMap({ new LinkedHashSet<InterferenceNode>() })

    BuildNodeToColoringNodeMap();
    variables.each { v -> 
      if(v.type == TempVarType.PARAM && v.id < 6)
        ColorNodeMustBe[GetColoringNodeUnsafe(v)] << Reg.getRegOfParamArgNum(v.id + 1);
    }

prof()
    // Now handle odd/node-specific cases.
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      BuildNodeToColoringNodeMap();

      // Add the interference edges.
      def liveVars = node.anno['regalloc-liveness']

      // Uncomment to see liveness analysis results.
//      dbgOut "Node: $node, numLiveVars = ${liveVars.size()}"
//      dbgOut "  ${liveVars.collect { it.id }}"

      // Extra edges to add to handle special cases.
      /*if(node instanceof LowIrValueNode && node.getDef()) {
        if(node.getDef().type == TempVarType.PARAM) {
          if(node.getDef().id < 6)
            ColorNodeMustBe[GetColoringNode(node.getDef())] << Reg.getRegOfParamArgNum(node.getDef().id + 1);
        }
      }*/

      switch(node) {
      case LowIrBinOp:
        // Handle modulo and division blocking.
        switch(node.op) {
        case BinOpType.DIV:
          ColorNodeMustBe[GetColoringNodeUnsafe(node.tmpVar)] << Reg.RAX;
          ColorNodeMustBe[GetColoringNodeUnsafe(node.leftTmpVar)] << Reg.RAX;        
          ColorsNodeCannotBe[GetColoringNodeUnsafe(node.rightTmpVar)] << Reg.RDX;
          liveVars.each {
            if(it != node.leftTmpVar && it != node.rightTmpVar) {
              ColorsNodeCannotBe[GetColoringNodeUnsafe(it)] << Reg.RAX
              ColorsNodeCannotBe[GetColoringNodeUnsafe(it)] << Reg.RDX;
            }
          }
          break;
        case BinOpType.MOD:
          ColorNodeMustBe[GetColoringNodeUnsafe(node.tmpVar)] << Reg.RDX
          ColorNodeMustBe[GetColoringNodeUnsafe(node.leftTmpVar)] << Reg.RAX;
          ColorsNodeCannotBe[GetColoringNodeUnsafe(node.rightTmpVar)] << Reg.RDX;
          liveVars.each {
            if(it != node.leftTmpVar && it != node.rightTmpVar) {
              ColorsNodeCannotBe[GetColoringNodeUnsafe(it)] << Reg.RAX
              ColorsNodeCannotBe[GetColoringNodeUnsafe(it)] << Reg.RDX;
            }
          }
          break;
        case BinOpType.SUB:
          AddEdge(new InterferenceEdge(GetColoringNodeUnsafe(node.tmpVar), GetColoringNodeUnsafe(node.rightTmpVar)));
          break;
        case BinOpType.LT:
        case BinOpType.LTE:
        case BinOpType.GT:
        case BinOpType.GTE:
        case BinOpType.EQ:
        case BinOpType.NEQ:
          // Not allowed to be r10 as that is used as a temporary.
          ColorsNodeCannotBe[GetColoringNodeUnsafe(node.getDef())] << Reg.R10;
          ColorsNodeCannotBe[GetColoringNodeUnsafe(node.leftTmpVar)] << Reg.R10;
          ColorsNodeCannotBe[GetColoringNodeUnsafe(node.rightTmpVar)] << Reg.R10;
          if(node.getSuccessors().size() == 1) {
            def nextNode = node.getSuccessors().first();
            def nextLiveVars = nextNode.anno['regalloc-liveness']
            nextLiveVars.each { nlv -> 
              if(nlv != node.getDef())
                ColorsNodeCannotBe[GetColoringNodeUnsafe(nlv)] << Reg.R10;
            }
          }
          break;
        default:
          break;
        }
        break;
      case LowIrLoad:
      case LowIrStore:
        if(node.index != null) {
          // We need to use r10 as a temporary to handle the index of the array.
          node.getUses().each { use -> 
            ColorsNodeCannotBe[GetColoringNodeUnsafe(use)] << Reg.R10;
          }
          liveVars.each { lv -> 
            if(lv != node.getDef())
              ColorsNodeCannotBe[GetColoringNodeUnsafe(lv)] << Reg.R10;
          }
        }
        break;
      case LowIrMethodCall:
      case LowIrCallOut:
        assert node.paramTmpVars.size() <= 6;
        def theParams = node.paramTmpVars.collect { it };
        node.paramTmpVars.eachWithIndex { ptv, i -> 
          ColorNodeMustBe[GetColoringNodeUnsafe(ptv)] << Reg.getRegOfParamArgNum(i + 1);
          liveVars.each { lv -> 
            assert lv != node.getDef()
            if(!theParams.contains(lv))
              ColorsNodeCannotBe[GetColoringNodeUnsafe(lv)] << Reg.getRegOfParamArgNum(i+1);
          }
        }
        // We also need to force the def-site to be RAX if the method isn't void.
        ColorNodeMustBe[GetColoringNodeUnsafe(node.tmpVar)] << Reg.RAX;
        if(node.getSuccessors().size() == 1) {
          def nextNode = node.getSuccessors().first();
          def nextLiveVars = nextNode.anno['regalloc-liveness']
          nextLiveVars.each { nlv -> 
            if(nlv != node.getDef())
              ColorsNodeCannotBe[GetColoringNodeUnsafe(nlv)] << Reg.RAX;
          }
        }
        break;
      default:
        break;
      }
    }
prof('traversed graph')

    dbgOut "finished traversing."

    //dbgOut "final ColorNodeMustBe = $ColorNodeMustBe"
    //dbgOut "final ColorsNodeCannotBe = $ColorsNodeCannotBe"

prof()
    BuildNodeToColoringNodeMap();
    ColorNodeMustBe.keySet().each { iNode ->
      ColorNodeMustBe[iNode].each { color -> 
        ForceNodeColor(iNode, color);      
      }
    }
prof('forcenodecolor')

prof()
    BuildNodeToColoringNodeMap();
    ColorsNodeCannotBe.keySet().each { iNode -> 
      ColorsNodeCannotBe[iNode].each { color -> 
        ForceNodeNotColor(iNode, color);
      }
    }
prof('forcenodenotcolor')

prof()
    UpdateAfterNodesModified();
prof('update after modified')
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
    Validate();

    assert a; assert b;
    a.nodes.each { node -> assert !(node instanceof RegisterTempVar) }
    b.nodes.each { node -> assert !(node instanceof RegisterTempVar) }

    if(a.isMovRelated() && b.isMovRelated()) {
      def queryOfAinB = a.movRelatedNodes.find { b.nodes.contains(it) };
      def queryOfBinA = b.movRelatedNodes.find { a.nodes.contains(it) };
      if(queryOfAinB && queryOfBinA) {
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

    InterferenceNode c = a.ResultOfCoalescingWith(b);
    AddNodeUnsafe(c);

    // Now we have to make sure to have transferred the edges.
    List<InterferenceEdge> edgesToAdd = []
    def needToUpdate = { curNode -> curNode == a || curNode == b }
    edges.each { e -> 
      if((needToUpdate(e.N1()) || needToUpdate(e.N2())) && (needToUpdate(e.N1()) != needToUpdate(e.N2()))) {
        InterferenceEdge updatedEdge = new InterferenceEdge(needToUpdate(e.N1()) ? c : e.N1(), needToUpdate(e.N2()) ? c : e.N2());
        //println "updatedEdge = $updatedEdge"
        updatedEdge.Validate();
        edgesToAdd << updatedEdge;
      }
    }

    edgesToAdd.each { AddEdgeUnsafe(it) }
    //RemoveMultipleNodes([a,b]);
    nodes.remove(a);
    nodes.remove(b);
    UpdateAfterNodesModified();
    Validate();
  }

  public void ForceNodeColor(InterferenceNode nodeToForce, Reg color) {
    assert nodeToForce;
    assert color;

    Reg.eachReg { r -> 
      if(r != color) 
        AddEdgeUnsafe(new InterferenceEdge(nodeToForce, GetColoringNodeUnsafe(r.GetRegisterTempVar())));
    }

    UpdateAfterEdgesModified();
  }

  public void ForceNodeNotColor(InterferenceNode nodeToForce, Reg color) {
    AddEdge(new InterferenceEdge(nodeToForce, GetColoringNodeUnsafe(color.GetRegisterTempVar())));
  }

  ColoringNode GetColoringNodeUnsafe(def tv) {
    assert nodeToColoringNode.containsKey(tv)
    return nodeToColoringNode[tv]
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

    if(nodeToColoringNode.keySet().contains(tv) == false) {
      println tv;
      println "nodeToColoringNode = "
      nodeToColoringNode.keySet().each { n -> 
        println " n = $n, cn = ${nodeToColoringNode[n]}"
      }
    }
    //assert nodeToColoringNode.keySet().contains(tv);
    //assert nodeToColoringNode[tv].nodes.contains(tv);
    return nodeToColoringNode[tv];
  }

  void AddMovRelation(TempVar src, TempVar dst) {
    assert src; assert dst;
    Validate();
    GetColoringNode(src).AddMovRelation(dst);
    GetColoringNode(dst).AddMovRelation(src);
    Validate();
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
    //println "Before it happens!"
    //println "iNode = $iNode"
    
    nodes.each { n ->
      iNode.nodes.each { i ->
        if(n.movRelatedNodes.contains(i)) {
          def opp = n.nodes.find({iNode.movRelatedNodes.contains(it)});
          assert opp;
          //movRelatedNodes.contains(
          n.RemoveMovRelation(i);
          iNode.RemoveMovRelation(opp);
        }
      }
    }
    LinkedHashSet<InterferenceNode> neighbors = GetNeighbors(iNode);
    Validate();
    //println "Right before it happens!"
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
    //assert variables;
    nodes.each { 
      assert it instanceof InterferenceNode;
      it.Validate();
    }
    edges.each {
      assert it instanceof InterferenceEdge;
      it.Validate();
    }
    //variables.each { assert it instanceof TempVar; }

    // now verify there are no duplicates between tempvars
    List<InterferenceNode> allRepresentedNodes = []
    nodes.each { node -> node.nodes.each { allRepresentedNodes << it } }
    assert (allRepresentedNodes.size() == (new LinkedHashSet(allRepresentedNodes)).size())

    // Now lets make sure mov-relations are always symmetric.
    nodes.each { n -> 
      n.movRelatedNodes.each { mrn -> 
        def hasRelation = false; 
        GetColoringNode(mrn).movRelatedNodes.each { mrn2 -> 
          if(n == GetColoringNode(mrn2))
            hasRelation = true;
        }
        if(!hasRelation) {
          println "B:LJDF: $n, $mrn, ${GetColoringNode(mrn)}"
        }
        assert hasRelation;
      }
    }
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
    Validate(); b.Validate();
    if(color || b.color)
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
    return "[INd, Rep = $representative, clr = $color, mr = ${isMovRelated()}, mrNodes = $movRelatedNodes, nodes = $nodes]"
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
    assert a instanceof InterferenceNode;
    assert b instanceof InterferenceNode;
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
    /*PerformSymmetric { cn1, cn2 -> 
      cn1.movRelatedNodes.each { mrn -> 
        if(mrn != cn2.representative)
          assert cn2.nodes.contains(mrn) == false;
      }
    }*/
  }
}
