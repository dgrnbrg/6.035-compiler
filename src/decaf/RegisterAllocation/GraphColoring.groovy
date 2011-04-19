package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class ColoringNode {
  def color;
  LinkedHashSet nodes;
  def anno = [:];

  public ColoringNode() {
    nodes = new LinkedHashSet()
    color = null
  }

  public ColoringNode(c) {
    nodes = new LinkedHashSet()
    color = c
  }
}

class ColoringEdge {
  ColoringNode cn1;
  ColoringNode cn2;

  public ColoringEdge(ColoringNode a, ColoringNode b) {
    assert a
    assert b
    cn1 = a
    cn2 = b
  }
}

class IncompatibleEdge extends ColoringEdge {
  public IncompatibleEdge(ColoringNode a, ColoringNode b) {
    super(a, b)
  }
}

class PreferredEdge extends ColoringEdge {}

class NeighborTable {
  def neighbors = [:]
  def degreeMap = [:]

  LinkedHashSet<ColoringNode> GetNeighbors(ColoringNode cn) {
    return neighbors[(cn)]
  }

  void BuildFromEdges(LinkedHashSet<ColoringEdge> edges) {
    // Populate neighborTable
    edges.each { edge -> 
      neighbors[(edge.cn1)] = new LinkedHashSet<ColoringNode>()
      neighbors[(edge.cn2)] = new LinkedHashSet<ColoringNode>()
    }

    edges.each { edge -> 
      neighbors[(edge.cn1)] += [edge.cn2]
      neighbors[(edge.cn2)] += [edge.cn1]
    }
  }

  void UpdateDegreeMap(LinkedHashSet<ColoringNode> nodes) {
    degreeMap = [:]

    nodes.each { node -> 
      def curNeighbors = GetNeighbors(node)
      def degree = (curNeighbors != null) ? curNeighbors.size() : 0
      if(!degreeMap[(degree)]) 
        degreeMap[(degree)] = new LinkedHashSet<ColoringNode>()
      degreeMap[(degree)] += [node]
    }
  }
}

class ColorableGraph {
  LinkedHashSet<ColoringNode> nodes;

  LinkedHashSet<IncompatibleEdge> incEdges;
  LinkedHashSet<PreferredEdge> prefEdges;

  NeighborTable IncompatibleNeighbors;
  NeighborTable PreferredNeighbors;
  
  public ColorableGraph() {
    nodes = []
    incEdges = []
    prefEdges = []
    IncompatibleNeighbors = new NeighborTable()
    PreferredNeighbors = new NeighborTable()
  }

  void EraseAllColorFromGraph() {
    assert nodes
    nodes.each { it.color = null }
  }

  void UpdateAfterNodesModified() {
    // remove edges that have nodes that don't exist
    assert incEdges != null
    LinkedHashSet<ColoringEdge> edgesToRemove = new LinkedHashSet<ColoringEdge>()
    incEdges.each { edge -> 
      if(!(nodes.contains(edge.cn1) && nodes.contains(edge.cn2))) 
        edgesToRemove += [edge]
    }
    edgesToRemove.each { incEdges.remove(it) }

    assert prefEdges != null
    edgesToRemove = new LinkedHashSet<ColoringEdge>()
    prefEdges.each { edge -> 
      if(!(nodes.contains(edge.cn1) && nodes.contains(edge.cn2))) 
        edgesToRemove += [edge]
    }
    edgesToRemove.each { prefEdges.remove(it) }

    UpdateAfterEdgesModified()
  }

  void UpdateAfterEdgesModified() {
    assert IncompatibleNeighbors
    IncompatibleNeighbors.BuildFromEdges(incEdges)
    IncompatibleNeighbors.UpdateDegreeMap(nodes)

    assert PreferredNeighbors    
    PreferredNeighbors.BuildFromEdges(prefEdges)
    PreferredNeighbors.UpdateDegreeMap(nodes)
  }
}

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











