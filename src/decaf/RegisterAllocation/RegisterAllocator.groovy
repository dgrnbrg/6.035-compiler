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
    debOut "Now running Build()."

    ig = new InterferenceGraph(methodDesc);
    CreateRegisterNodes();

    def tv2CN = BuildNodeToColoringNodeMap()
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrMov) {
        tv2CN[(node.src)].AddMovRelation(node.dst);
        tv2CN[(node.dst)].AddMovRelation(node.src);
      }
    }
  }

  void AddNodeToColoringStack(InterferenceNode node) {
    assert false;
    // 1. Remove it from ig.
    // 2. Push it onto the coloringStack.
  }

  // Simplify tries to simplify a single node. (hence run to fixed-point)
  boolean Simplify() {
    debOut "Now running Simplify()."

    // Determine is there is a non-move-related node of low (< K) degree in the graph.
    def nodeToSimplify = nodes.find { cn -> 
      (!cn.isMovRelated() && ig.neighborTable.getDegree(cn) < sigDeg())
    }

    if(nodeToSimplify == null) 
      return false;
    
    AddNodeToColoringStack(nodeToSimplify);
    return true;
  }

  // Coalesce coalesces a single node each call returning false if nothing to coalesce
  boolean Coalesce() {
    dbgOut "Now running Coalescing."

    // Perform conservative coalescing. Iterate through all the pairs of 
    // move-related nodes and try and coalesce the first one found.
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
        n.movRelatedNodes.removeAll { true }
        return true;
      }
    }

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
    dbgOut "Now handling a spilled node: $spilledNode"
    InterferenceNode spilledNode = SelectNodeToSpill();
    assert ig.isSigDeg(spilledNode)

    SpillVar sv = new SpillVar();

    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      // Determine if this node uses the spilled var.
      assert(false) // see line below
      if(true) {
        // If so, add in a LowIrLoadSpillVar
        dbgOut "Adding LowIrLoadSpillVar: Detected use at $node"
        assert false
      }

      // Determine if this node defines the spilled var.
      assert false; // See the condition on line below.
      if(true) {
        // If so, add in a LowIrStoreSpillVar
        dbgOut "Adding LowIrStoreSpillVar: Detected def at $node"
        assert false
      }
    }
  }
}

