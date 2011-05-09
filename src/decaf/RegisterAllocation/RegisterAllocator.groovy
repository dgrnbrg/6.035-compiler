package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import static decaf.Reg.eachRegNode

public class RegisterAllocator {
  def dbgOut = DbgHelper.dbgOut
  def dbgOutFreeze = { str -> if(true) dbgOut(str) };
  def dbgOutCoalesce = { str -> if(true) dbgOut(str) };
  def dbgOutSimplify = { str -> if(true) dbgOut(str) };
  def dbgOutSpill = { str -> if(true) dbgOut(str) };
  
	MethodDescriptor methodDesc;
  InterferenceGraph ig;
  LinkedHashSet<RegisterTempVar> registerNodes;
  ColoringStack theStack;
  LinkedHashSet<TempVar> spillWorklist;

  LinkedHashSet<String> colors = new LinkedHashSet(
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9',  'r10', 'r11', 'r12', 'r13', 'r14', 'r15'])

  public RegisterAllocator(MethodDescriptor md) {
    assert(md)
    methodDesc = md
    ig = null;
  }

  void RunRegAllocToFixedPoint() {
    dbgOut "Beginning Register Allocation."

    // First break method calls and callouts.
    RegAllocLowIrModifier.BreakCalls(methodDesc)
    RegAllocLowIrModifier.BreakDivMod(methodDesc)
    RegAllocLowIrModifier.MarkUselessIntLiterals(methodDesc)
    int numIterations = 0;
    while(RegAllocationIteration()) {
      println "iteration $numIterations completed"
      numIterations++;
    }

    dbgOut "Finished Register Allocation."
  }

  void ColorLowIr() {
    dbgOut "Now actually coloring the graph. (LowIr being modified)"

    // Build the map from tmpVar to color
    def tmpVarToRegTempVar = [:]
    def tmpVarToReg = [:]
    ig.BuildNodeToColoringNodeMap();
    ig.nodes.each { node -> 
      node.nodes.each { n ->
        assert node.color;
        assert n instanceof TempVar
        tmpVarToRegTempVar[n] = node.color.GetRegisterTempVar();
        tmpVarToReg[n] = node.color;
      }
    }

    dbgOut "This is the coloring map:"
    tmpVarToRegTempVar.keySet().each { 
      if(!(it instanceof RegisterTempVar))
        dbgOut "$it --> ${tmpVarToRegTempVar[it]}"
    }

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      node.SwapUsesUsingMap(tmpVarToRegTempVar)
      node.SwapDefUsingMap(tmpVarToRegTempVar);
    }

    tmpVarToRegTempVar.keySet().each { tv -> 
      if(tv.type == TempVarType.PARAM && tv.id >= 6)
        methodDesc.svManager.FlagOneOfPostSixArgsForColoring(tv, tmpVarToReg[tv]);
    }
  }

  boolean OnlySigDegOrMoveRelated() {
    assert ig; assert ig.nodes;
    for(node in ig.nodes) { 
      if(!(ig.isSigDeg(node) || node.isMovRelated()))
        if(!(node.representative instanceof RegisterTempVar))
          return false;
    }
    return true;
  }

  // Run this function to fixed point. It implements the flow-diagram in MCIJ in 11.2
  boolean RegAllocationIteration() {
    dbgOut "Now running an iteration of the register allocator."

    Build();

    while(true) {
      boolean didSimplifyHappen = Simplify();
      if(didSimplifyHappen)
        while(Simplify());

      boolean didCoalesce = Coalesce();
      if(didCoalesce)
        while(Coalesce());

      if(!OnlySigDegOrMoveRelated())
        continue;

      if(!didSimplifyHappen && !didCoalesce)
        if(Freeze())
          continue;

      if(PotentialSpill())
        continue;

      break;
    }

    //assert OnlySigDegOrMoveRelated();
    //ig.nodes.each { assert it.isMovRelated() == false }

    //println "ig.nodes.size() is ${ig.nodes.size()}"
    //ig.nodes.each { println it }
    if(!Select()) {
      Spill();
      return true;
    }

    return false;
  }

  void Build() {
    dbgOut "Now running Build()."

    ig = new InterferenceGraph(methodDesc);
    theStack = new ColoringStack(ig);    
    ig.Validate();
    spillWorklist = [];

    ig.BuildNodeToColoringNodeMap()
    def tvLIILR = RegAllocStatistics.GetLowIrIntLiteralRelatedTempVars(methodDesc);
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrMov) {
        if(tvLIILR.contains(node.src) == false && tvLIILR.contains(node.dst) == false) {
        ig.AddMovRelation(node.src, node.dst)
        ig.Validate();
        }
      }
    }

    //ig.neighborTable.PrettyPrint();

    ig.Validate();

    dbgOut "Finished running Build()."
  }

  boolean FastSimplify() {
    // Determine is there is a non-move-related node of low (< K) degree in the graph.
    LinkedHashSet<InterferenceNode> nodesToSimplify = ig.nodes.findAll { cn -> 
      if(cn.isMovRelated() && ig.isSigDeg(cn))
        return false;
      else if(cn.representative instanceof RegisterTempVar)
        return false;
      return true;
    }

    if(nodesToSimplify.size() > 0) {
      nodesToSimplify.each { nts -> 
        theStack.PushNodeFromGraphToStack(nts);
      }
      //dbgOut "+  simplified: $nodeToSimplify."
      return true;
    }

    dbgOut "- Could not find a node to simplify."
    return false;
  }

  boolean Simplify() {
    dbgOut "Now running Simplify()."
    // Determine is there is a non-move-related node of low (< K) degree in the graph.
    ig.Validate();
    InterferenceNode nodeToSimplify = null;
    int curMinDegree = 100000;

    ig.nodes.each { iNode -> 
      //if(iNode.isMovRelated() && ig.isSigDeg(iNode))
      //  return;
      if(iNode.isMovRelated() || ig.isSigDeg(iNode))
        return;
      else if(iNode.representative instanceof RegisterTempVar)
        return;
      int thisDegree = ig.neighborTable.GetDegree(iNode);
      if(nodeToSimplify == null || thisDegree < curMinDegree) {
        curMinDegree = thisDegree;
        nodeToSimplify = iNode;
      }
    }

    if(nodeToSimplify != null) {
      dbgOut "+ Found a node to simplify: $nodeToSimplify"
      theStack.PushNodeFromGraphToStack(nodeToSimplify);
      ig.Validate();
      return true;
    }

    ig.Validate();
    dbgOut "- Could not find a node to simplify."
    return false;
  }

  boolean Coalesce() {
    dbgOutCoalesce "Now running Coalescing."
    ig.Validate();

    //println "ig.nodes.size() is ${ig.nodes.size()}"
    //ig.nodes.each { println it }

    // Perform conservative coalescing. Nothing fancy here.
    for(pair in [ig.nodes, ig.nodes].combinations()) { 
      if(pair[0] != pair[1]) {
        if(!(pair[0].representative instanceof RegisterTempVar) && 
            !(pair[1].representative instanceof RegisterTempVar)) {
          if(ig.CanCoalesceNodes(pair[0], pair[1])) {
            dbgOutCoalesce "Found a pair of nodes to coalesce: $pair"
            ig.Validate()
            ig.CoalesceNodes(pair[0], pair[1]);
            ig.Validate();
            return true;
          }
        }
      }
    }

    ig.Validate();
    dbgOutCoalesce "Finished trying to coalesce (no more coalesces found!)."
    return false;
  }

  boolean Freeze() {
    dbgOutFreeze "Now running Freeze()."
    ig.Validate();

    for(n in ig.nodes) { 
      if(n.isMovRelated() && !ig.isSigDeg(n)) {
        ig.Validate();
        dbgOutFreeze "Found a freeze-able node: $n"
        //assert false;
        n.Freeze();
        /*n.movRelatedNodes.each { mrn -> 
          ig.GetColoringNode(mrn).RemoveMovRelation(n.representative)
        }*/
        //assert n.movRelatedNodes.size() == 0;
        //n.movRelatedNodes = new LinkedHashSet();
        ig.Validate();
        return true;
      }
    }

    dbgOutFreeze "No freeze-able nodes found."
    ig.Validate();
    return false;
  }

  boolean PotentialSpill() {
    dbgOut "Now calculating potential spills."
    ig.Validate();

    // need to check that there are no low-degree 
    for(node in ig.nodes) { 
      if(ig.isSigDeg(node) && ((node.representative instanceof RegisterTempVar) == false)) {
        dbgOut "Found potential spill: $node"
        node.nodes.each { spillWorklist << it; }
        ig.Validate();
        theStack.PushNodeFromGraphToStack(node);
        ig.Validate();
        return true;
      }
    }
    
    ig.Validate();
    dbgOut "Did not find any potential spills."
    return false
  }

  boolean Select() {
    dbgOut "Now running select." 

    // Need to pop each node off the stack and try and color it.
    while(!theStack.isEmpty()) {
      if(theStack.TryPopNodeFromStackToGraph() == false) {
        dbgOut "Have to spill. Terminating select process." 
        return false;
      }
    }

    dbgOut "Finished running select, theStack.isEmpty() = ${theStack.isEmpty()}."
    return true;
  }

  TempVar SelectTempVarToSpill() {
    def tvLFS = RegAllocStatistics.GetTempVarsLoadedFromSpills(methodDesc);
    def tvSTS = RegAllocStatistics.GetTempVarsStoredToSpills(methodDesc)
    
    def goodSpills = []
    def badSpills = []

    spillWorklist.each { tv -> 
      if(tvLFS.contains(tv) || tvSTS.contains(tv))
        badSpills << tv
      else
        goodSpills << tv;
    }

    if(goodSpills.size() + badSpills.size() != spillWorklist.size())
      assert false;

    if(goodSpills.size() > 0)
      return goodSpills.first()

    if(badSpills.size() == 0) {
      println "badSpills = $badSpills"
      println "spillWorklist = $spillWorklist"
      assert false;
    }
    return badSpills.first();
    //return theStack.Peek().node.representative;
  }

  void Spill() {
    dbgOutSpill "Have to spill."
    TempVar tempVarToSpill = SelectTempVarToSpill();
    dbgOutSpill "Spilling the TempVar: $tempVarToSpill"

    SpillVar sv = null;

    if(tempVarToSpill.type == TempVarType.PARAM) {
      if(tempVarToSpill.id < 6) {
        methodDesc.svManager.FlagOneOfFirstSixArgsForSpilling(tempVarToSpill);
        sv = methodDesc.svManager.firstSixFlags[tempVarToSpill];
      } else {
        methodDesc.svManager.FlagOneOfPostSixArgsForSpilling(tempVarToSpill);
        sv = methodDesc.svManager.postSixSpillFlags[tempVarToSpill];
        assert sv instanceof PostSixParamSpillVar;
      }
    } else {
      sv = methodDesc.svManager.requestNewSpillVar();
    }

    assert sv;

    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      // Determine if this node uses the spilled var.
      if(node.getUses().contains(tempVarToSpill)) {
        dbgOutSpill "USE. SPILL. TV = $tempVarToSpill, Node = $node"
        TempVar tv = methodDesc.tempFactory.createLocalTemp();
        node.SwapUsesUsingMap([(tempVarToSpill) : tv]);
        PlaceSpillLoadBeforeNode(node, sv, tv);
      }

      // Determine if this node defines the spilled var.
      if(tempVarToSpill == node.getDef()) {
        dbgOutSpill "DEF. SPILL. TV = $tempVarToSpill, Node = $node"
        TempVar tv = methodDesc.tempFactory.createLocalTemp();
        node.SwapDefUsingMap([(node.getDef()) : tv])
        PlaceSpillStoreAfterNode(node, sv, tv);
      }
    }

    dbgOutSpill "Finished spilling the node."
  }

  void PlaceSpillStoreAfterNode(LowIrNode siteOfSpill, SpillVar sv, TempVar tv) {
    assert siteOfSpill; assert tv; assert sv;
    RegAllocLowIrModifier.PlaceNodeAfterNode(siteOfSpill, new LowIrStoreSpill(value : tv, storeLoc : sv));
  }

  void PlaceSpillLoadBeforeNode(LowIrNode siteOfSpill, SpillVar sv, TempVar tv) {
    assert siteOfSpill; assert tv; assert sv;
    RegAllocLowIrModifier.PlaceNodeBeforeNode(new LowIrLoadSpill(tmpVar : tv, loadLoc : sv), siteOfSpill);
  }

  // Warning, this function should not be called on LowIrCondJump.
  void PlaceNodeAfterNode(LowIrNode beforeNode, LowIrNode afterNode) {
    assert beforeNode; assert afterNode;
    assert !(beforeNode instanceof LowIrCondJump);
    assert beforeNode.getSuccessors().size() == 1;

    LowIrNode.link(beforeNode, afterNode);
    LowIrNode.link(afterNode, beforeNode.getSuccessors().first())
    LowIrNode.unlink(beforeNode, beforeNode.getSuccessors().first())
  }

  void PlaceNodeBeforeNode(LowIrNode beforeNode, LowIrNode afterNode) {
    assert beforeNode; assert afterNode;
    assert afterNode.getPredecessors().size() > 0;

    LowIrNode.link(beforeNode, afterNode);
    def pred = afterNode.getPredecessors();
    pred.each { p -> 
      LowIrNode.unlink(p, afterNode);
      LowIrNode.link(p, beforeNode);
      if(p instanceof LowIrCondJump) {
        if(p.trueDest == afterNode) 
          p.trueDest = beforeNode;
        if(p.falseDest == afterNode)
          p.falseDest = beforeNode;
      }
    }
  }
}














