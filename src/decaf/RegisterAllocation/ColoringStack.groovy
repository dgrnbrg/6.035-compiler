package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import static decaf.Reg.eachRegNode

public class ColoringStack {
  List<ColoringStackBlock> theStack;
  InterferenceGraph ig;

  public ColoringStack(InterferenceGraph ig) {
    assert ig;
    this.ig = ig;
    theStack = new ArrayList<ColoringStackBlock>([]);
  }

  void PushNodeFromGraphToStack(InterferenceNode iNode) {
    assert iNode;
    Validate();
    assert ig.nodes.contains(iNode);

    LinkedHashSet<InterferenceNode> neighbors = ig.GetNeighborsAndThenRemoveNode(iNode);
    LinkedHashSet<TempVar> allNeighbors = [];
    neighbors.each { n ->
      n.nodes.each { allNeighbors << it }
    }
    theStack.push(new ColoringStackBlock(iNode, allNeighbors));

    Validate();
  }

  void PushMultipleNodesFromGraphToStack(List<InterferenceNode> iNodes) {
    assert iNodes;
    Validate();
    // iNodes.each { ig.nodes.contains(iNode) };
    LinkedHashMap neighborMap = ig.GetMultipleNeighborsAndThenRemoveNode(iNodes);
  }

  // This is the function that chooses the color. The listed coloring 
  // heuristic is obviously non-optimal.
  Reg PickColor() {
    ColoringStackBlock csb = Peek();
    ig.BuildNodeToColoringNodeMap();
    LinkedHashSet<Reg> remainingColors = []
    Reg.eachReg { r -> 
      if(r != Reg.RSP && r != Reg.RBP)
        remainingColors << r;
    };

    assert remainingColors.size() == 14;

    csb.interferenceNeighbors.each { n -> 
      assert n instanceof TempVar;
      InterferenceNode iNode = ig.GetColoringNode(n);
      if(iNode.color)
        remainingColors.remove(iNode.color)
    }

    if(remainingColors.size() > 0)
      return remainingColors.asList().first();

    return null;
  }

  boolean TryPopNodeFromStackToGraph() {
    Validate();
    assert theStack.size() > 0;
    ColoringStackBlock curCSB = Peek();
    Reg color = PickColor();

    assert color != Reg.RSP && color != Reg.RBP

    if(color == null)
      return false; // Didn't find color so spill.

    curCSB = theStack.pop()
    curCSB.node.SetColor(color);
    ig.AddNodeWithPreExistingNeighbors(curCSB.node, curCSB.interferenceNeighbors);
    assert ig.nodes.contains(curCSB.node);
    Validate();
    return true;
  }

  boolean ContainsTempVar(TempVar tv) {
    for(csb in theStack) {
      if(csb.ContainsTempVar(tv))
        return true;
    }
    return false;
  }

  boolean isEmpty() {
    return (theStack.size() == 0);
  }

  ColoringStackBlock Peek() {
    assert theStack.size() > 0;
    return theStack.last();
  }

  void Validate() {
    if(!DbgHelper.dbgValidationOn)
      return;
    assert theStack != null;
    assert ig;
    theStack.each { 
      assert it instanceof ColoringStackBlock;
      it.Validate();
    }
  }

  void PrettyPrint() {
    assert false;
  }
}

class ColoringStackBlock {
  InterferenceNode node;
  LinkedHashSet<TempVar> interferenceNeighbors;

  ColoringStackBlock(InterferenceNode node, LinkedHashSet<TempVar> interNeighbors) {
    assert node; assert interNeighbors != null;
    node.Validate();
    this.node = node;
    this.interferenceNeighbors = interNeighbors;
  }

  boolean ContainsTempVar(TempVar tv) {
    assert node;
    for(n in node.getAllRepresentedNodes()) {
      assert n instanceof TempVar;
      if(n == tv)
        return true;
    }
    return false;
  }

  void Validate() {
    if(!DbgHelper.dbgValidationOn)
      return;
    assert node; assert interferenceNeighbors != null;
    node.Validate();
  }

  String toString() {
    return "${[node : this.node, interNeigh : this.interferenceNeighbors ]}"
  }
}
