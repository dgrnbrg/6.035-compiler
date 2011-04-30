package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

public class ColorableGraph {
  def dbgOut = { str -> assert str; println str; }

  NeighborTable neighborTable;
  LinkedHashSet<ColoringNode> nodes;
  LinkedHashSet<ColoringEdge> edges;  
  LinkedHashMap nodeToColoringNode;

  ColorableGraph() {
    nodes = new LinkedHashSet<ColoringNode>([]);
    edges = new LinkedHashSet<ColoringEdge>([]);
    neighborTable = new NeighborTable(nodes, edges);
    nodeToColoringNode = new LinkedHashMap();
  }

  void AddNode(ColoringNode cn) {
    cn.Validate();
    assert !nodes.contains(cn);
    nodes << cn;
    assert nodes.contains(cn);
    UpdateAfterNodesModified();
  }

  void RemoveNode(ColoringNode cn) {
    assert cn; 
    cn.Validate();
    assert nodes.contains(cn);
    nodes.remove(cn);
    assert !nodes.contains(cn);
    UpdateAfterNodesModified();
  }
  
  void AddEdge(ColoringEdge ce) {
    assert ce; 
    ce.Validate();
    assert nodes.contains(ce.cn1);
    assert nodes.contains(ce.cn2);
    assert ce.cn1 != ce.cn2;
    assert !edges.contains(ce.cn1);
    println "Adding the edge $ce"
    edges << ce;
    assert edges.contains(ce);
    UpdateAfterEdgesModified();
    assert GetNeighbors(ce.cn1).contains(ce.cn2);
    assert GetNeighbors(ce.cn2).contains(ce.cn1);
  }

  void RemoveEdge(ColoringEdge ce) {
    assert ce; 
    ce.Validate();
    assert nodes.contains(ce.cn1);
    assert nodes.contains(ce.cn2);
    assert edges.contains(ce);
    edges.remove(ce);
    assert !edges.contains(ce);
    UpdateAfterEdgesModified();
  }

  int GetDegree(ColoringNode node) {
    return neighborTable.GetDegree(node)
  }

  LinkedHashSet<ColoringNode> GetNeighbors(ColoringNode node) {
    assert node;
    neighborTable.Build(nodes, edges)
    return neighborTable.GetNeighbors(node);
  }

  void UpdateAfterNodesModified() {
    // remove edges that have nodes that don't exist
    LinkedHashSet<ColoringEdge> edgesToRemove = [];
    edges.each { ce ->
      if(!(nodes.contains(ce.cn1) && nodes.contains(ce.cn2)))
        edgesToRemove << ce;
    }
    edgesToRemove.each { edges.remove(it) }

    BuildNodeToColoringNodeMap();
    UpdateAfterEdgesModified()
  }

  void UpdateAfterEdgesModified() {
    neighborTable.Build(nodes, edges)
  }
  
  void BuildNodeToColoringNodeMap() {
    nodeToColoringNode = new LinkedHashMap();

    nodes.each { cn ->       
      cn.getNodes().each { n -> 
        nodeToColoringNode[n] = cn
      }
    }
  }

  ColoringNode GetColoringNode(def node) {
    assert false;
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

  public void Validate() {
    assert false;
  }
}

public class NeighborTable {
  // map from coloring node to the set of it's neighbors
  LinkedHashMap neighbors = [:]

  // map from a degree value (integer) to the coloring nodes with that degree
  // (using whatever graph structure the Build function takes in).
  LinkedHashMap degreeMap = [:]

  public NeighborTable(nodes, edges) {
    assert nodes != null; assert edges != null;
    println "Creating neighbor table."
    println "nodes = "
    nodes.each { println it }
    println "edges = "
    edges.each { println it }
    Build(nodes, edges)
  }

  LinkedHashSet<ColoringNode> GetNeighbors(ColoringNode cn) {
    assert neighbors != null;
    assert cn;
    cn.Validate();
    assert neighbors[cn] != null;
    return neighbors[cn]
  }

  int GetDegree(ColoringNode cn) {
    assert neighbors != null;
    assert neighbors[(cn)] != null;
    return neighbors[(cn)].size();
  }

  void Build(LinkedHashSet<ColoringNode> nodes, LinkedHashSet<ColoringEdge> edges) {
    assert nodes != null; assert edges != null;
    //println "building the neighbor table. nodes.size() = ${nodes.size()}, edges.size() = ${edges.size()}"

    neighbors = [:];

    // Populate neighborTable
    nodes.each { n -> neighbors[n] = new LinkedHashSet(); }

    edges.each { edge -> 
      //println "adding an edge to the neighbor table: $edge"
      assert neighbors[edge.cn1].contains(edge.cn2) == false;
      neighbors[edge.cn1] << edge.cn2;
      //println "${neighbors[edge.cn1]}"
      assert neighbors[edge.cn2].contains(edge.cn1) == false;
      neighbors[edge.cn2] << edge.cn1;
      //println "${neighbors[edge.cn2]}"
    }

    //neighbors.keySet().each { println "[$it, ${neighbors[it]}]" }
    BuildDegreeMap(nodes);
  }

  void BuildDegreeMap(LinkedHashSet<ColoringNode> nodes) {
    degreeMap = [:]

    nodes.each { node -> 
      def curNeighbors = GetNeighbors(node)
      def degree = (curNeighbors != null) ? curNeighbors.size() : 0
      if(!degreeMap[degree]) 
        degreeMap[degree] = new LinkedHashSet<ColoringNode>();
      degreeMap[degree] << node;
    }
  }

  void PrettyPrint() {
    println "Here is the neighbor table... Omitted entries for RegisterTempVars."
    neighbors.keySet().each { n -> 
      if(n.representative instanceof RegisterTempVar == false) {
        println "$n"
        neighbors[n].each { 
          if(!(it.representative instanceof RegisterTempVar))
            println "  $it"
        }
      }
    }
    println "------------------------------"
  }
}

public class ColoringNode {
  Reg color = null;
  def representative = null;
  LinkedHashSet nodes = new LinkedHashSet();

  public ColoringNode() {
    assert false;
  }

  public ColoringNode(node) {
    assert node;
    representative = node;
    nodes = new LinkedHashSet([representative])
  }

  public String toString() {
    return "[ColoringNode. Rep = $representative, color = $color]"
  }

  public LinkedHashSet getNodes() {
    return nodes;
  }

  public SetColor(Reg r) {
    assert r; assert this.color == null;
    this.color = r;
  }

  public void Validate() {
    assert false;
  }
}

public class ColoringEdge {
  ColoringNode cn1;
  ColoringNode cn2;

  public ColoringEdge(ColoringNode a, ColoringNode b) {
    assert a; assert b;
    cn1 = a;
    cn2 = b;
  }

  public void Validate() {
    assert false;
  }
}
