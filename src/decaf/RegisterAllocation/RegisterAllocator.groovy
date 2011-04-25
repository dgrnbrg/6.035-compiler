package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class RegisterAllocator {
  def dbgOut = { str -> assert str; println str; }

	MethodDescriptor methodDesc;
  InterferenceGraph ig;

  LinkedHashSet colors = new LinkedHashSet(
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9',  'r10', 'r11', 'r12', 'r13', 'r14', 'r15'])

  public RegisterAllocator(MethodDescriptor md) {
    assert(md)
    methodDesc = md
  }

  void ReplaceVars() {
    assert tempVarToColor
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      assert (node instanceof LowIrNode)
      /*switch(node) {
        case 
      }*/
    }
  }

  void RunRegAllocToFixedPoint() {
    while(ig.nodes.size() > 0)
      RegAllocationIteration();
  }

  // Run this function to fixed point. It implements the flow-diagram in MCIJ in 11.2
  boolean RegAllocationIteration() {
    dbgOut "Now running an iteration of the register allocator."
    Build();
  
    boolean foundPotentialSpill = false;

    while(true) {
      boolean didSimplifyHappen = Simplify();
      while(Simplify());

      boolean didCoalesce = Coalesce();
      while(Coalesce());

      if(!OnlySigDegOrMoveRelated())
        continue;

      if(!didCoalesce && !didCoalesce) {
        while(Freeze());
        continue;
      }

      def potSpills = []

      while(true) {
        def ps = PotentialSpill(ig)
        if(ps)
          potSpills += [ps]
        else 
          continue
      }

      break;
    }

    assert(!OnlySigDegOrMoveRelated())

    // Try assigning colors to the graph
    if(!Select()) {
      HandleSpill();
      return false;
    } 

    // No sigDeg spills required, now check for required spills.
    if(HandleHighDegSpills())
      // A spill occured, we handled it and then we have to rebuild!
      return false;

    // No spills... so we're done!
    return true;  
  }

  void CreateRegisterNodes() {
    // Create the 14 extra nodes for registers
    colors.each { color -> 
      ig.addNode(
        new ColoringNode(
          color: color, 
          representative: new RegisterTempVar(registerName: color, type: TempVarType.REGISTER),
          nodes: new LinkedHashSet()))
    }
    ig.UpdateAfterNodesModified()
  }

  void Build() {
    debOut "Now running Build()."

    // Build the interference graph.
    cg = new ColorableGraph();
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

  // Simplify tries to simplify a single node. (hence run to fixed-point)
  boolean Simplify() {
    debOut "Now running Simplify()."

    // Determine is there is a non-move-related node of low (< K) degree in the graph.
    def nodeToSimplify = nodes.find { cn -> 
      (!cn.isMovRelated() && ig.neighborTable.getDegree(cn) < sigDeg())
    }

    if(nodeToSimplify == null) 
      return false;
    
    ig.removeNode(nodeToSimplify);
    ig.coloringStack.push(nodeToSimplify);
    return true;
  }

  // Coalesce coalesces a single node each call returning false if nothing to coalesce
  boolean Coalesce() {
    debOut "Now running Coalescing."

    // Perform conservative coalescing.

    // Iterate through all the pairs of move-related nodes and try and coalesce
    // the first one found

    // Make a list of all the 
    assert false

    return false;
  }

  boolean Freeze() {
    dbgOut "Now running Freeze()."

    // Determine if there is a move-related node of low-degree.
    def mrnld = ig.moveRelatedNodes.findAll { 
      ig.getDegree(it) < ig.sigDeg()
    }

    mrnld.each { cn -> 
      if(cn.isMoveRelated()) {
        // Freeze the node.
        dbgOut "mrnld.size() = ${mrnld.size()}, Freezing the node: $cn"
        assert false
        return true
      }
    }

    return false;
  }

  ColoringNode PotentialSpill() {
    dbgOut "Now calculating potential spills."

    ig.getNodes.each { cn -> 
      if(ig.getDegree(cn) == ig.sigDeg()) {
        // This is a potential spill!
        dbgOut "Found potential spill: $cn"
        return cn
      }
    }
    
    dbgOut "Did not find any potential spills."
    return null
  }

  boolean Select() {
    dbgOut "Now running select." 

    // Successful coloring!
    assert false;
    return true;

    // Have to spill. :(
    assert false;
    return false;
  }

  void HandleSpill(ColoringNode spilledNode) {
    dbgOut "Now handling a spilled node: $spilledNode"
    assert spilledNode;
    assert spilledNode.nodes.size() == 1

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





























