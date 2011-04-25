package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

class ColoringNode {
  def color;
  def representative;
  def frozen = false;
  LinkedHashSet otherNodes;
  LinkedHashSet movRelatedNodes;
  def anno = [:];

  public ColoringNode() {}

  ColoringNode CoalesceWith(ColoringNode b) {
    ColoringNode c = new ColoringNode();
    c.otherNodes = nodes + b.otherNodes + new LinkedHashSet([b.representative])
    c.representative = representative;
    c.anno = anno + b.anno;
    
    if(color == null && b.color == null) c.color = null
    else if(color == null && b.color != null) c.color = b.color
    else if(color != null && b.color == null) c.color = a.color
    else 
      // Both are colors, so they should be the same.
      if(color != b.color)
        assert false;

    c.movRelatedNodes = movRelatedNodes + b.movRelatedNodes;
    c.UpdateMoveRelatedNodes();

    return c;
  }

  public String toString() {
    return "[ColoringNode. Rep = $representative, color = $color]"
  }

  LinkedHashSet getAllRepresentedNodes() {
    return (new LinkedHashSet([representative])) + (otherNodes ? otherNodes : [])
  }

  boolean isMovRelated() {
    assert movRelatedNodes;
    if(frozen) 
      return false;
    return (movRelatedNodes.size() > 0)
  }

  void UpdateMoveRelatedNodes() {
    getAllRepresentedNodes().each { movRelatedNodes.remove(it) }
  }

  void AddMovRelation(TempVar tv) {
    otherNodes += [tv]
  }
}

class ColoringEdge {
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
    nodes.each { n -> 
      neighbors[(n)] = new LinkedHashSet<ColoringNode>()    
    }

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
      if(!degreeMap[(degree)]) 
        degreeMap[(degree)] = new LinkedHashSet<ColoringNode>()
      degreeMap[(degree)] += [node]
    }
  }
}

class ColorableGraph {
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
  
  void addEdge(ColoringEdge ce) {
    assert ce; assert !edges.contains(ce); 
    edges += [ce];
  }

  void removeEdge(ColoringEdge ce) {
    assert ce; assert edges.contains(ce);
    edges.remove(ce);
  }

  int sigDeg() {
    assert false;
  }

  int getDegree(ColoringNode node) {
    return neighborTable.degreeMap[(node)]
  }

  LinkedHashSet<ColoringNode> getNeighbors(ColoringNode node) {
    assert node; assert IncompatibleNeighbors.neighborMap[(node)];
    return IncompatibleNeighbors.neighborMap[(node)]
  }

  boolean CanCoalesceNodes(ColoringNode a, ColoringNode b, NeighborTable nt) {
    assert a; assert b; 
    assert nodes.contains(a) && nodes.contains(b);

    assert false; // Need to refactor the following line:
    assert a.isMoveRelated() && b.isMoveRelated();

    LinkedHashSet<ColoringNode> sumNeighbors = nt.GetNeighbors(a) + nt.GetNeighbors(b);
    return (sumNeighbors.size() < sigDeg())
  }

  void CoalesceNodes(ColoringNode a, ColoringNode b, NeighborTable nt) {
    assert a; assert b;
    assert nodes.contains(a) && nodes.contains(b)
    assert CanCoalesceNodes(a, b, nt);

    nodes.remove(a);
    nodes.remove(b);

    ColoringNode c = a.CoalesceWith(b);
    addNode(c);

    // Now we have to make sure to have transferred the edges.
    def edgesToAdd = []
    edges.each { e -> 
      def newCN1 = e.cn1, newCN2 = e.cn2;
      if(newCN1 == a || newCN1 == b) newCN1 = c;
      if(newCN2 == a || newCN2 == b) newCN2 = c;
      if(newCN1 == c || newCN2 == c) 
        edgesToAdd += [new ColoringEdge(newCN1, newCN2)];
    }

    edgesToAdd.each { addEdge(it) }

    UpdateAfterNodesModified();
  }

  void UpdateAfterNodesModified() {
    // remove edges that have nodes that don't exist
    edges.each { edge -> 
      if(!(nodes.contains(edge.cn1) && nodes.contains(edge.cn2))) 
        removeEdge(edge);
    }

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

  void VerifyNoDuplicates() {
    dbgOut "Verifying that there are no duplicates throughout the colorable graph."
    def allRepresentedNodes = []
    nodes.each { n -> 
      n.getAllRepresentedNodes().each { 
        allRepresentedNodes += [it]
      }
    }

    assert (allRepresentedNodes.size() == new LinkedHashSet(allRepresentedNodes))
  }

  void VerifyMoveRelations() {
    def moveRelatedCNs = nodes.findAll { cn -> cn.isMovRelated() }
    def n2cnMap = BuildNodeToColoringNodeMap();

    moveRelatedCNs.each { cn -> 
      cn.getAllRepresentedNodes().each { n -> 
        
    
  }
}

