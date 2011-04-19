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

