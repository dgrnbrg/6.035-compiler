package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*


/* ASSUMPTIONS:
	1. I assume that all parameter tempVars will be explicitly loaded 
			from memory into a register. To do this, I essentially auto-spill 
			all the paramTempVars.
*/

class RegisterAllocator {
  def dbgOut = { str -> 
    assert str
    println str;
  }

	MethodDescriptor methodDesc;
  InterferenceGraph ig;
  ColorableGraph cg;

  LinkedHashSet colors = new LinkedHashSet(
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9',  'r10', 'r11', 'r12', 'r13', 'r14', 'r15'])

  public RegisterAllocator(MethodDescriptor md) {
    assert(md)
    methodDesc = md
  }
  
  public ComputeInterferenceGraph() {
  	ig = new InterferenceGraph(methodDesc)
  	ig.CalculateInterferenceGraph()
    ig.ColorGraph(16)
  }

  

  void DoGraphColoring() {
    println "Doing Graph Coloring!"
    gc = new GraphColoring()
    
    assert ig
    
    ig.variables.each { v -> 
      ColoringNode cn = new ColoringNode()
      cn.nodes = new LinkedHashSet<ColoringNode>([v])
      gc.cg.nodes += [cn]
    }

    gc.cg.UpdateAfterNodesModified()

    ig.InterferenceEdges.each { ie -> 
      ColoringNode v1 = tempVarToColoringNode[((ie as List)[0])]
      ColoringNode v2 = tempVarToColoringNode[((ie as List)[1])]
      gc.cg.incEdges += [new IncompatibleEdge(v1, v2)]
    }

    gc.cg.UpdateAfterEdgesModified()

    gc.NaiveColoring()

    gc.tempVarToColor
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
    while(RegAllocationIteration());
  }

  // Run this function to fixed point. It implements the flow-diagram in MCIJ in 11.2
  boolean RegAllocationIteration() {
    dbgOut "Now running an iteration of the register allocator."
    Build();
  
    def foundPotentialSpill = false;

    while(true) {
      boolean didSimplifyHappen = Simplify();
      while(Simplify()); // Keep running until nothing left to simplify

      boolean didCoalesce = Coalesce();
      while(Coalesce());

      // Simplify and coalesce are repeated until only significant degree or 
      // move related nodes remainl.
      if(!OnlySigDegOrMoveRelated())
        continue;

      if(!didCoalesce && !didCoalesce) {
        while(Freeze()):
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
      // Spill required
      HandleSpill()
      return false;
    } 

    // No sigDeg spills required, now check for required spills.
    if(HandleHighDegSpills())
      // A spill occured, we handled it and then we have to rebuild!
      return false;

    // No spills... so we're done!
    return true;  
  }

  def BuildTempVarToColoringNodeMap() {
    def tempVarToColoringNode = [:]
    ig.variables.each { v -> tempVarToColoringNode[(v)] = cn }
    return tempVarToColoringNode
  }

  void AddInterferenceEdges() {
    assert ig
    def tv2CN = BuildTempVarToColoringNodeMap()

    cg.AddIncompatibleEdges(
      ig.InterferenceEdges.collect { ie -> 
        ColoringNode v1 = tv2CN[((ie as List)[0])]
        ColoringNode v2 = tv2CN[((ie as List)[1])]
        return new IncompatibleEdge(v1, v2);
      }
    )
  }

  void CreateRegisterNodes() {
    // Create the 14 extra nodes for registers
    LinkedHashSet<ColoringNode> registerNodes = 
      new LinkedHashSet<ColoringNode> (
        colors.collect { color -> 
          new ColoringNode(
            color, 
            new LinkedHashSet(
              [new RegisterTempVar(registerName : color, type : TempVarType.REGISTER)]
            ))})

    cg.nodes += registerNodes
    cg.UpdateAfterNodesModified()
  }

  InterferenceGraph Build() {
    debOut "Now running Build()."

    // Build the interference graph.
    cg = new ColorableGraph();
    ig = new InterferenceGraph(methodDesc);
    ig.CalculateInterferenceGraph();

    CreateRegisterNodes();

    // First we'll calculate the move-related variables
    def movRelatedNodes = new LinkedHashSet()
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrMov)
        movRelatedNodes += [node.src, node.dst]
    }

    cg.addNodes( ig.variables.collect { v -> 
        def newNode = new ColoringNode(color: null, nodes = new LinkedHashSet(v))
        if(movRelatedNodes.contains(newNode)) newNode.movRelated = true
        return newNode
      }
    )

    AddInterferenceEdges();
  }

  // Simplify tries to simplify a single node. (hence run to fixed-point)
  boolean Simplify() {
    debOut "Now running Simplify()."

    // Determine is there is a non-move-related node of low (< K) degree in the interference graph.
    if(assert(false)) {
      // Remove the node from the interference graph
      assert false;
      return true;
    }

    return false;
  }

  // Coalesce coalesces a single node each call returning false if nothing to coalesce
  boolean Coalesce() {
    debOut "Now running Coalescing."

    // Perform conservative coalescing.

    // Iterate through all the pairs of move-related nodes and try and coalesce
    // the first one found
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

  ColoringNode PotentialSpill(InterferenceGraph ig) {
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
    assert false;
  }

  void HandleSpill(ColoringNode spilledNode) {
    dbgOut "Now handling a spilled node: $spilledNode"
    assert spilledNode;
    assert spilledNode.nodes.size() == 1

    SpillVar sv = new SpillVar();

    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      // Determine if this node uses the spilled var.
      if(assert(false)) {
        // If so, add in a LowIrLoadSpillVar
        dbgOut "Adding LowIrLoadSpillVar: Detected use at $node"
        assert false
      }

      // Determine if this node defines the spilled var.
      if(assert(false)) {
        // If so, add in a LowIrStoreSpillVar
        dbgOut "Adding LowIrStoreSpillVar: Detected def at $node"
        assert false
      }
    }
  }
}





























