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

  public ColoringNode(c, n) {
    assert c; assert n;
    color = c; nodes = n;
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

class PreferredEdge extends ColoringEdge {
  public PreferredEdge(ColoringNode a, ColoringNode b) {
    super(a, b)
  }
}

class NeighborTable {
  // map from coloring node to the set of it's neighbors
  def neighbors = [:]
  // map from a degree value (integer) to the coloring nodes with that degree
  // (using whatever graph structure the Build function takes in).
  def degreeMap = [:]

  LinkedHashSet<ColoringNode> GetNeighbors(ColoringNode cn) {
    return neighbors[(cn)]
  }

  void Build(LinkedHashSet<ColoringNode> nodes, LinkedHashSet<ColoringEdge> edges) {
    // Populate neighborTable
    nodes.each { n -> 
      neighbors[(n)] = new LinkedHashSet<ColoringNode>()    
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

  void CoalesceNodes(ColoringNode a, ColoringNode b) {
    assert a; assert b    ;
    assert nodes.contains(a) && nodes.contains(b)
    nodes.remove(a)
    nodes.remove(b)

    ColoringNode c = new ColoringNode();
    c.nodes = a.nodes + b.nodes
    c.anno = a.anno + b.anno
    
    if(a.color == null && b.color == null) c.color = null
    else if(a.color == null && b.color != null) c.color = b.color
    else if(a.color != null && b.color == null) c.color = a.color
    else {
      // Must both be colors. they should be the same.
      if(a.color != b.color)
        assert false;
    }

    nodes += [c]
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
    assert nodes; assert IncompatibleNeighbors; assert (incEdges != null);
    IncompatibleNeighbors.Build(nodes, incEdges)
    IncompatibleNeighbors.UpdateDegreeMap(nodes)

    assert PreferredNeighbors; assert (prefEdges != null);   
    PreferredNeighbors.Build(nodes, prefEdges)
    PreferredNeighbors.UpdateDegreeMap(nodes)
  }
}

