package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class RegisterAllocator {
  def dbgOut = { str -> assert str; println str; }

	MethodDescriptor methodDesc;
  InterferenceGraph ig;
  LinkedHashSet<InterferenceNode> spillWorklist;

  LinkedHashSet colors = new LinkedHashSet(
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9',  'r10', 'r11', 'r12', 'r13', 'r14', 'r15'])

  public RegisterAllocator(MethodDescriptor md) {
    assert(md)
    methodDesc = md
  }

  void RunRegAllocToFixedPoint() {
    while(RegAllocationIteration());

    DoColoring();
  }

  void DoColoring() {
    assert false;
  }

  // Run this function to fixed point. It implements the flow-diagram in MCIJ in 11.2
  boolean RegAllocationIteration() {
    dbgOut "Now running an iteration of the register allocator."
    Build();

    spillWorkList = new LinkedHashSet<InterferenceNode>();

    while(true) {
      boolean didSimplifyHappen = Simplify();
      while(Simplify());

      boolean didCoalesce = Coalesce();
      while(Coalesce());

      if(!OnlySigDegOrMoveRelated())
        continue;

      if(!didCoalesce && !didCoalesce)
        if(!Freeze())
          continue;

      if(PotentialSpill(ig))
        continue;

      break;
    }

    assert(!OnlySigDegOrMoveRelated())
    ig.nodes.each { assert it.isMoveRelated() == false }

    if(!Select()) {
      Spill();
      return true;
    }

    return false;
  }

  void CreateRegisterNodes() {
    // Create the 14 extra nodes for registers
    colors.each { color -> 
      ig.addNode(
        new InterferenceNode(
          color: color, 
          representative: new RegisterTempVar(registerName: color, type: TempVarType.REGISTER),
          nodes: new LinkedHashSet()))
    }
  }

  void Build() {
    dbgOut "Now running Build()."

    ig = new InterferenceGraph(methodDesc);
    CreateRegisterNodes();

    def tv2CN = BuildNodeToColoringNodeMap()
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrMov) {
        tv2CN[(node.src)].AddMovRelation(node.dst);
        tv2CN[(node.dst)].AddMovRelation(node.src);
      }
    }
    dbgOut "Finished running Build()."
  }

  void AddNodeToColoringStack(InterferenceNode node) {
    assert false;
    // 1. Remove it from ig.
    // 2. Push it onto the coloringStack.
  }

  boolean Simplify() {
    debOut "Now running Simplify()."

    // Determine is there is a non-move-related node of low (< K) degree in the graph.
    def nodeToSimplify = nodes.find { cn -> (!cn.isMovRelated() && ig.isSigDeg(cn)) }

    if(nodeToSimplify == null) {
      dbgOut "Could not find a node to simplify."
      return false;
    }
    
    AddNodeToColoringStack(nodeToSimplify);
    dbgOut "Simplify() removed a node: $nodeToSimplify."
    return true;
  }

  boolean Coalesce() {
    dbgOut "Now running Coalescing."

    // Perform conservative coalescing. Nothing fancy here.
    [ig.nodes, ig.nodes].combinations().each { pair -> 
      if(pair[0] != pair[1])
        if(ig.CanCoalesceNodes(pair[0], pair[1])) {
          dbgOut "Found a pair of nodes to coalesce: $pair"
          ig.CoalesceNodes(pair[0], pair[1]);
          return true;
        }
    }

    dbgOut "Finished trying to coalesce (no more coalesces found!)."
    return false;
  }

  boolean Freeze() {
    dbgOut "Now running Freeze()."

    ig.nodes.each { n -> 
      if(n.isMovRelated() && !ig.isSigDeg()) {
        // Found a freeze-able node.
        dbgOut "Foudn a freeze-able node: $n"
        n.frozen = true;
        def tv2CNMap = ig.BuildNodeToColoringNodeMap();
        n.movRelatedNodes.each { mrn -> 
          tv2CNMap[(mrn)].RemoveMovRelation(n.representative)
        }
        n.movRelatedNodes = new LinkedHashSet();
        return true;
      }
    }

    dbgOut "No freeze-able nodes found."
    return false;
  }

  boolean PotentialSpill() {
    dbgOut "Now calculating potential spills."

    // need to check that there are no low-degree 
    ig.nodes.each { node -> 
      if(ig.isSigDeg(node)) {
        dbgOut "Found potential spill: $node"
        spillWorklist += [node]
        AddNodeToColoringStack(node);
        return node;
      }
    }
    
    dbgOut "Did not find any potential spills."
    return null
  }

  boolean PopNodeAndTryToColor() {
    assert false;
  }

  boolean Select() {
    dbgOut "Now running select." 

    // Need to pop each node off the stack and try and color it.
    while(coloringStack.size() > 0) {
      if(!PopNodeAndTryToColor())
        return false;
    }

    assert spillWorklist.size() == 0;
    return true;
  }

  InterferenceNode SelectNodeToSpill() {
    // Obviously you want something better than this.
    spillWorklist.asList().first();
  }

  void Spill() {
    InterferenceNode spilledNode = SelectNodeToSpill();
    assert ig.isSigDeg(spilledNode)
    dbgOut "Now handling a spilled node: $spilledNode"

    SpillVar sv = new SpillVar();

    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      // Determine if this node uses the spilled var.
      node.getUses().intersect(spilledNode.nodes).each { use -> 
        TempVar tv = methodDesc.tempFactory.createLocalTemp();
        node.SwapUsesUsingMap([(use) : tv])
        PlaceSpillLoadBeforeNode(node, sv, tv);
      }

      // Determine if this node defines the spilled var.
      if(spilledNode.nodes.contains(node.getDef())) { 
        TempVar tv = methodDesc.tempFactory.createLocalTemp();
        node.SwapDefUsingMap([(node.getDef()) : tv])
        PlaceSpillStoreAfterNode(node, sv, tv);
      }
    }

    dbgOut "Finished spilling the node."
  }

  void PlaceSpillStoreAfterNode(LowIrNode siteOfSpill, SpillVar sv, TempVar tv) {
    assert siteOfSpill; assert tv; assert sv;
    assert siteOfSpill.getSuccessors().size() == 1;

    LowIrLoadSpill nodeToPlace = new LowIrStoreSpill(tmpVar : tv, loadLoc : sv);
    
    LowIrNode.link(siteOfSpill, nodeToPlace);
    LowIrNode.link(nodeToPlace, siteOfSpill.getSuccessors().first())
    LowIrNode.unlink(siteOfSpill, siteOfSpill.getSuccessors().first())
  }

  void PlaceSpillLoadBeforeNode(LowIrNode siteOfSpill, SpillVar sv, TempVar tv) {
    assert siteOfSpill; assert tv; assert sv;
    assert siteOfSpill.getPredecessors().size() > 0;

    LowIrStoreSpill nodeToPlace = new LowIrStoreSpill(value : tv, storeLoc : sv);
    
    LowIrNode.link(nodeToPlace, siteOfSpill);
    def pred = siteOfSpill.getPredecessors();
    pred.each { p -> 
      LowIrNode.unlink(p, siteOfSpill);
      LowIrNode.link(p, nodeToPlace);
      if(p instanceof LowIrCond) {
        if(p.trueDest == siteOfSpill)
          p.trueDest = nodeToPlace;
        if(p.falseDest == siteOfSpiill)
          p.falseDest = nodeToPlace;
      }
    }
  }
}


























