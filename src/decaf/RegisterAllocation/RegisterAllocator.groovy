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
	MethodDescriptor methodDesc;
  InterferenceGraph ig;
  GraphColoring gc;
  
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
    def tempVarToColoringNode = [:]
    ig.variables.each { v -> 
      ColoringNode cn = new ColoringNode()
      cn.nodes = new LinkedHashSet<ColoringNode>([v])
      tempVarToColoringNode[(v)] = cn
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

  // Run this function to fixed point. It implements the flow-diagram in 
  // MCIJ in 11.2
  boolean RegColorIteration() {
  
    Build(graph);
  
    def foundPotentialSpill = false;

  while(true) {

    // Simplify removes a single node each call
    // returning false if nothign to simplify.
    boolean didSimplifyHappen = Simplify();
    while(Simplify()); // Keep running until nothing left to simplify

    // Coalesce coalesces a single node each call,
    // returning false if nothing to coalesce
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
  if(!TryAssignColors()) {
    // Spill required
    HandleSpill()
    return false;
  } 

  // No sigDeg spills required, now check for required spills.
  if(HandleHighDegSpills()) {
    // A spill occured, we handled it and then we have to rebuild!
    return false;
  }

  // No spills... so we're done!
  return true;  
}

}





























