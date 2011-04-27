package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import decaf.optimizations.*

class InterferenceNode extends ColoringNode {
  LinkedHashSet movRelatedNodes;
  def frozen = false;
  
  public InterferenceNode() { }

  public InterferenceNode(TempVar tv) {
    super(tv);
    if(tv instanceof RegisterTempVar) {
      assert tv.registerName;
      color = tv.registerName;
    }
  }

  public InterferenceNode ResultOfCoalescingWith(InterferenceNode b) {
    assert !(color != null && b.color != null && color != b.color);

    InterferenceNode c = 
      new InterferenceNode(
        representative : representative, 
        nodes : nodes + b.nodes, 
        movRelatedNodes : movRelatedNodes + b.movRelatedNodes,
        color : (color != null) ? color : b.color);
    c.UpdateMoveRelatedNodes();

    return c;
  }

  boolean isMovRelated() {
    assert movRelatedNodes;
    return (frozen ? false : (movRelatedNodes.size() > 0))
  }

  void UpdateMoveRelatedNodes() {
    assert nodes
    nodes.each { movRelatedNodes.remove(it) }
  }

  void AddMovRelation(n) {
    assert n; assert n instanceof TempVar;
    movRelatedNodes += [n];
  }

  void RemoveMovRelation(n) {
    assert n; assert movRelatedNodes.contains(n);
    movRelatedNodes.remove(n);
  }

  public String toString() {
    return "[InterferenceNode. Rep = $representative, color = $color, frozen = $frozen]"
  }

  public void Validate() {
    assert representative;
    assert representative instanceof TempVar;
    assert nodes;
    nodes.each { assert it instanceof TempVar }
    assert nodes.contains(representative);
    assert movRelatedNodes;
    movRelatedNodes.each { assert it instanceof TempVar }
    assert (movRelatedNodes.intersect(nodes)).size() == 0;
  }
}

class InterferenceEdge extends ColoringEdge {
  public InterferenceEdge(ColoringNode a, ColoringNode b) {
    super(a, b)
  }

  public String toString() {
    return "[InterferenceEdge. cn1 = $cn1, cn2 = $cn2]"
  }

  public void Validate() {
    assert cn1; assert cn2;
    assert cn1 instanceof InterferenceNode;
    assert cn2 instanceof InterferenceNode;
    cn1.Validate();
    cn2.Validate();
    if(cn1.color && cn2.color)
      assert cn1.color != cn2.color;
    cn1.movRelatedNodes.each { mrn -> 
      if(mrn != cn2.representative)
        assert cn2.nodes.contains(mrn) == false;
    }
    cn2.movRelatedNodes.each { mrn -> 
      if(mrn != cn1.representative)
        assert cn1.nodes.contains(mrn) == false;
    }
  }
}
