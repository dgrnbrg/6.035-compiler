package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

abstract class ColoringNode {
  def color;
  def representative;
  def nodes = new LinkedHashSet();

  public ColoringNode() {}

  public ColoringNode(node) {
    assert node;
    representative = node;
    nodes = new LinkedHashSet([representative])
  }

  public String toString() {
    return "[ColoringNode. Rep = $representative, color = $color]"
  }
}

abstract class ColoringEdge {
  ColoringNode cn1;
  ColoringNode cn2;

  public ColoringEdge(ColoringNode a, ColoringNode b) {
    assert a; assert b;
    cn1 = a;
    cn2 = b;
  }
}

class NeighborTable {
  // map from coloring node to the set of it's neighbors
  def neighbors = [:]
  // map from a degree value (integer) to the coloring nodes with that degree
  // (using whatever graph structure the Build function takes in).
  def degreeMap = [:]

  public NeighborTable(nodes, edges) {
    assert nodes; assert edges;
    Build(nodes, edges)
  }

  LinkedHashSet<ColoringNode> GetNeighbors(ColoringNode cn) {
    assert neighbors != null;
    assert neighbors[(cn)];
    return neighbors[(cn)]
  }

  int GetDegree(ColoringNode cn) {
    assert neighbors != null;
    assert neighbors[(cn)];
    return neighbors[(cn)].size();
  }

  void Build(LinkedHashSet<ColoringNode> nodes, LinkedHashSet<ColoringEdge> edges) {
    neighbors = [:];

    // Populate neighborTable
    nodes.each { n -> neighbors[(n)] = new LinkedHashSet<ColoringNode>(); }

    edges.each { edge -> 
      neighbors[(edge.cn1)] += [edge.cn2]
      neighbors[(edge.cn2)] += [edge.cn1]
    }

    BuildDegreeMap(nodes);
  }

  void BuildDegreeMap(LinkedHashSet<ColoringNode> nodes) {
    degreeMap = [:]

    nodes.each { node -> 
      def curNeighbors = GetNeighbors(node)
      def degree = (curNeighbors != null) ? curNeighbors.size() : 0
      if(!degreeMap[(degree)]) degreeMap[(degree)] = new LinkedHashSet<ColoringNode>();
      degreeMap[(degree)] += [node]
    }
  }
}
