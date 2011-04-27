package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

abstract class ColorableGraph {
  def dbgOut = { str -> assert str; println str; }

  LinkedHashSet<ColoringNode> nodes;
  LinkedHashSet<ColoringEdge> edges;
  NeighborTable neighborTable;
  def coloringStack = []

  public ColorableGraph() {
    coloringStack = [];
    nodes = new LinkedHashSet()
    edges = new LinkedHashSet()
    neighborTable = new NeighborTable()
  }

  def GetAvailableColors = { node -> 
    LinkedHashSet takenColors = []
    cg.neighborTable.GetNeighbors(node).each { cn -> 
      if(cn.color) 
        takenColors += [cn.color]
    }

    return colors - takenColors
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

  def removeNodes = { nodesToRemove -> 
    assert nodesToRemove;
    nodesToRemove.each { nodes.remove(it) }
    UpdateAfterNodesModified();
  }
  
  void addEdge(ColoringEdge ce) {
    assert ce; assert !edges.contains(ce); 
    edges += [ce];
    UpdateAfterEdgesModified();
  }

  def addEdges = { ces -> 
    assert ces; 
    edges += ces;
    UpdateAfterEdgesModified();
  }

  void removeEdge(ColoringEdge ce) {
    assert ce; assert edges.contains(ce);
    edges.remove(ce);
    UpdateAfterEdgesModified();
  }

  def removeEdges = { ces -> 
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
  
  def BuildNodeToColoringNodeMap() {
    def tempVarToColoringNode = [:]

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

