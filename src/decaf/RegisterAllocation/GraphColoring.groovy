package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class GraphColoring {
  ColorableGraph cg;
  def tempVarToColor = [:]

  

  public GraphColoring() {
    cg = new ColorableGraph()
  }

  

  void NaiveColoring() {
    // Setting up the ColorableGraph
    
    

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

    // Pop nodes off the stack and color them.
    while(stack.size() > 0) {
      ColoringNode cn = stack.pop()
      LinkedHashSet AvailableColors = cg.GetAvailableColors(cn)
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
}

