package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class GraphColoring {
  ColorableGraph cg;

  public GraphColoring() {
    cg = new ColorableGraph()
  }

  void NaiveColoring() {
    cg.EraseAllColorFromGraph()

    // Now create 14 extra nodes for registers
    LinkedHashSet colors = new LinkedHashSet(
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
      'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15'])

    LinkedHashSet<ColoringNode> registerNodes = 
      new LinkedHashSet<ColoringNode>(
        colors.collect { color -> new ColoringNode(color) }
      )

    cg.nodes += registerNodes
    cg.UpdateAfterNodesModified() 

    println cg.nodes
    
    def maxDegree = cg.IncompatibleNeighbors.degreeMap.keySet().max()
    println "Max Degree = $maxDegree"
    println cg.IncompatibleNeighbors.degreeMap.keySet().sort()
    assert maxDegree < colors.size()

    def stack = []

    cg.IncompatibleNeighbors.degreeMap.keySet().sort().each { deg -> 
      cg.IncompatibleNeighbors.degreeMap[deg].each { cn -> 
        if(cn.color == null)
          stack.push(cn)
      }
    }

    def GetColorsOfNeighbors = { node -> 
      LinkedHashSet takenColors = []
      cg.IncompatibleNeighbors.neighbors[(node)].each { cn -> 
        if(cn.color)
          takenColors += [cn.color]
      }

      return colors - takenColors
    }

    while(stack.size() > 0) {
      ColoringNode cn = stack.pop()
      LinkedHashSet AvailableColors = GetColorsOfNeighbors(cn)
      assert AvailableColors.size() > 0
      assert cn.color == null
      cn.color = (AvailableColors as List).first()
      println "Colored $cn with the color: $cn.color"
    }

    println "Finished coloring."
    
  }
}











