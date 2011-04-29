package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class RegisterAllocator {
  def dbgOut = { str -> assert str; println str; }

	MethodDescriptor methodDesc;
  InterferenceGraph ig;
  LinkedHashSet<InterferenceNode> spillWorklist;
  LinkedHashSet<RegisterTempVar> registerNodes;

  LinkedHashSet<String> colors = new LinkedHashSet(
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9',  'r10', 'r11', 'r12', 'r13', 'r14', 'r15'])

  public RegisterAllocator(MethodDescriptor md) {
    assert(md)
    methodDesc = md
    ig = null;
  }

  void RunRegAllocToFixedPointAndColor() {
    while(RegAllocationIteration());

    DoColoring();
  }

  void DoColoring() {
    // Build the map from tmpVar to color
    tmpVarToRegTempVar = [:]
    ig.BuildNodeToColorNodeMap();
    ig.variables().each { v -> 
      tmpVarToRegTempVar[(tv)] = RegColor.getRegColorFromName(ig.GetColoringNode(v).color);
    }

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      node.SwapUsesUsingMap(tmpVarToRegTempVar)
      node.SwapDefUsingMap(tmpVarToRegTempVar);
    }
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

      if(!didSimplifyHappen && !didCoalesce)
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

  void Build() {
    dbgOut "Now running Build()."

    ig = new InterferenceGraph(methodDesc);
    RegColor.eachRegNode { rn -> ig.addNode(rn); }

    ig.BuildNodeToColoringNodeMap()
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrMov) {
        ig.GetColoringNode(node.src).AddMovRelation(node.dst);
        ig.GetColoringNode(node.dst).AddMovRelation(node.src);
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
    InterferenceNode nodeToSimplify = nodes.find { cn -> (!cn.isMovRelated() && ig.isSigDeg(cn)) }

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
        n.Freeze();
        ig.BuildNodeToColoringNodeMap();
        n.movRelatedNodes.each { mrn -> 
          GetColoringNode(mrn).RemoveMovRelation(n.representative)
        }
        assert n.movRelatedNodes.size() == 0;
        n.movRelatedNodes = new LinkedHashSet();
        return true;
      }
    }

    dbgOut "No freeze-able nodes found."
    return false;
  }

  InterferenceNode PotentialSpill() {
    dbgOut "Now calculating potential spills."

    // need to check that there are no low-degree 
    ig.nodes.each { node -> 
      if(ig.isSigDeg(node)) {
        dbgOut "Found potential spill: $node"
        spillWorklist << node;
        AddNodeToColoringStack(node);
        return node;
      }
    }
    
    dbgOut "Did not find any potential spills."
    return null
  }

  boolean PopNodeAndTryToColor() {
    // Note here, or in Select() (or both), we need to add code that tries 
    // to color (a b -> x) such that b = x. Specifically, consider 
    // add src, dest. Then this is (src dest -> dest).
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

    SpillVar sv = methodDesc.svManager.requestNewSpillVar();

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
      if(p instanceof LowIrCondJump) {
        if(p.trueDest == siteOfSpill) 
          p.trueDest = nodeToPlace;
        if(p.falseDest == siteOfSpiill)
          p.falseDest = nodeToPlace;
      }
    }
  }
}
