

class ColoringNode {
  def color;
  def nodes = [];
  
  def anno = [:];
}

class ColoringEdge {
  ColoringNode cn1;
  ColoringNode cn2;
}

class IncompatibleEdge extends ColoringEdge {}
class PreferredEdge extends ColoringEdge {}

class NeighborTable {
  def neighbors = [:]
  def degreeMap = [:]

  LinkedHashSet<ColoringNode> GetNeighbors(ColoringNode cn) {
    assert neighbors[(cn)]
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

  void UpdateDegreeMap() {
    degreeMap = [:]

    neighbors.keySet().each { key -> 
      def degree = GetNeighbors(key).size()           
      if(!degreeMap[(degree)]) 
        degreeMap[(degree)] = new LinkedHashSet<ColoringNode>()
      degreeMap[(degree)] += [key]
    }
  }
}

class ColorableGraph {
  LinkedHashSet<ColoringNode> nodes;

  LinkedHashSet<IncompatibleEdge> incEdges;
  LinkedHashSet<PreferredEdge> prefEdges;

  NeighborTable IncompatibleNeighbors;
  NeighborTable PreferredNeighbors;
  
  void EraseAllColorFromGraph() {
    nodes.each { it.color = null }
  }

  void UpdateAfterNodesModified() {
    // remove edges that have nodes that don't exist
    LinkedHashSet<ColoringEdge> edgesToRemove = new LinkedHashSet<ColoringEdge>()
    incEdges.each { edge -> 
      if(!(nodes.contains(edge.cn1) && nodes.contains(edge.cn2))) 
        edgesToRemove += [edge]
    }

    edgesToRemove.each { incEdges.remove(it) }

    edgesToRemove = new LinkedHashSet<ColoringEdge>()
    prefEdges.each { edge -> 
      if(!(nodes.contains(edge.cn1) && nodes.contains(edge.cn2))) 
        edgesToRemove += [edge]
    }

    edgesToRemove.each { prefEdges.remove(it) }

    UpdateAfterEdgesModified()
  }

  void UpdateAfterEdgesModified() {
    IncompatibleNeighbors.BuildFromEdges(incEdges)
    PreferredNeighbors.BuildFromEdges(prefEdges)
    IncompatibleNeighbors.UpdateDegreeMap()
    PreferredNeighbors.UpdateDegreeMap()
  }
}

class GraphColoring {



}











