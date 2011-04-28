package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class ColorableGraph {
  def dbgOut = { str -> assert str; println str; }

  LinkedHashSet<ColoringNode> nodes;
  LinkedHashSet<ColoringEdge> edges;
  NeighborTable neighborTable;

  public ColorableGraph() {
    nodes = new LinkedHashSet<ColoringNode>([]);
    edges = new LinkedHashSet<ColoringEdge>([]);
    neighborTable = new NeighborTable();
  }

  LinkedHashSet GetIllegalColors = { node -> 
    LinkedHashSet takenColors = []
    cg.neighborTable.GetNeighbors(node).each { cn -> 
      if(cn.color) 
        takenColors += [cn.color]
    }

    return takenColors
  }

  void addNode(ColoringNode cn) {
    assert cn;
    nodes += new LinkedHashSet<ColoringNode>(coloringNodes)
  }

  void removeNode(ColoringNode cn) {
    assert cn; assert nodes.contains(cn);
    nodes.remove(cn);
    UpdateAfterNodesModified();
  }

  void removeNodes(List<ColoringNode> nodesToRemove) { 
    assert nodesToRemove;
    nodesToRemove.each { nodes.remove(it) }
    UpdateAfterNodesModified();
  }
  
  void addEdge(ColoringEdge ce) {
    assert ce; assert !edges.contains(ce); 
    edges += [ce];
    UpdateAfterEdgesModified();
  }

  void addEdges(Collection<ColoringEdge> ces) { 
    assert ces; 
    edges += ces;
    UpdateAfterEdgesModified();
  }

  void removeEdge(ColoringEdge ce) {
    assert ce; assert edges.contains(ce);
    edges.remove(ce);
    UpdateAfterEdgesModified();
  }

  void removeEdges(Collection<ColoringEdge> ces) { 
    assert ces;
    ces.each { edges.remove(it) }
    UpdateAfterEdgesModified();
  }

  int getDegree(ColoringNode node) {
    return neighborTable.GetDegree(node)
  }

  LinkedHashSet<ColoringNode> getNeighbors(ColoringNode node) {
    assert node;
    return neighborTable.GetNeighbors(node);
  }

  void UpdateAfterNodesModified() {
    // remove edges that have nodes that don't exist
    edges.retainAll { e -> nodes.contains(e.cn1) && nodes.contains(e.cn2) }
    UpdateAfterEdgesModified()
  }

  void UpdateAfterEdgesModified() {
    neighborMap.Build(nodes, edges)
  }
  
  LinkedHashMap BuildNodeToColoringNodeMap() {
    def tempVarToColoringNode = new LinkedHashMap();

    nodes.each { cn -> 
      cn.getAllRepresentedNodes.each { n -> 
        tempVarToColoringNode[(v)] = cn
      }
    }

    return tempVarToColoringNode
  }

  void DrawDotGraph(String fileName) {
    def extension = 'pdf'
    def graphFile = filename + '.' + 'ColorGraph' + '.' + extension
    dbgOut "Writing colorable graph output to $graphFile"

    def dotCommand = "dot -T$extension -o $graphFile"
    Process dot = dotCommand.execute()
    def dotOut = new PrintStream(dot.outputStream)

    def varToLabel = { "TVz${it.id}z${it.type}" }
    dbgOut 'digraph g {'
    dotOut.println 'digraph g {'
    edges.each { edge -> 
      def pairOfVariables = edge.collect { it }
      assert pairOfVariables.size() == 2      
      def v1 = pairOfVariables[0], v2 = pairOfVariables[1]
      dbgOut "${varToLabel(v1)} -> ${varToLabel(v2)}"
      dotOut.println "${varToLabel(v1)} -> ${varToLabel(v2)}"
    }
    dbgOut '}'
    dotOut.println '}'
    dotOut.close()
  }
}

