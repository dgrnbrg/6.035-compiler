package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class GraphColoring {
  ColorableGraph cg;
  def tempVarToColor = [:]

  LinkedHashSet colors = new LinkedHashSet(
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9',  'r10', 'r11', 'r12', 'r13', 'r14', 'r15'])

  public GraphColoring() {
    cg = new ColorableGraph()
  }

  void NaiveColoring() {
    // Setting up the ColorableGraph

    // Create the 14 extra nodes for registers
    LinkedHashSet<ColoringNode> registerNodes = 
      new LinkedHashSet<ColoringNode>(
        colors.collect { color -> 
          new ColoringNode(
            color, 
            new LinkedHashSet(
              [new RegisterTempVar(registerName : color, type : TempVarType.REGISTER)]
            )
          ) 
        }
      )

    cg.nodes += registerNodes
    cg.UpdateAfterNodesModified() 
    
    println "cg.nodes is now:"
    cg.nodes.each { node -> println "${node.nodes}, color = ${node.color}" }

    // Now you have the ability to force the color of a node to be a particular value.
    def forceColorOnNode = { node, color -> 
    
    }

    def maxDegree = cg.IncompatibleNeighbors.degreeMap.keySet().max()
    println "Max Degree = $maxDegree, degrees = ${cg.IncompatibleNeighbors.degreeMap.keySet().sort()}"
    assert maxDegree < colors.size()

    // Now setup stack and push nodes onto it.
    def stack = []

    cg.IncompatibleNeighbors.degreeMap.keySet().sort().each { deg -> 
      cg.IncompatibleNeighbors.degreeMap[deg].each { cn -> 
        if(cn.color == null)
          stack.push(cn)
      }
    }

    def GetAvailableColors = { node -> 
      LinkedHashSet takenColors = []
      cg.IncompatibleNeighbors.neighbors[(node)].each { cn -> 
        if(cn.color)
          takenColors += [cn.color]
      }

      return colors - takenColors
    }

    // Pop nodes off the stack and color them.
    while(stack.size() > 0) {
      ColoringNode cn = stack.pop()
      LinkedHashSet AvailableColors = GetAvailableColors(cn)
      assert AvailableColors.size() > 0
      assert cn.color == null
      cn.color = (AvailableColors as List).first()
    }

    // Now fill in tempVarToColor
    tempVarToColor = [:]

    cg.nodes.each { node -> 
      assert (node instanceof ColoringNode)
      node.nodes.each { tv -> 
        assert (tv instanceof TempVar)
        tempVarToColor[(tv)] = node.color
      }
    }

    println "Here is the map tempVarToColor:"
    tempVarToColor.keySet().each { key -> 
      println " $key = ${tempVarToColor[(key)]}"
    }

    println "Finished coloring."
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

