package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import static decaf.RegColor.eachRegNode

public class RegisterAllocator {
  def dbgOut = { str -> assert str; println str; }

	MethodDescriptor methodDesc;
  InterferenceGraph ig;
  LinkedHashSet<InterferenceNode> spillWorklist;
  LinkedHashSet<RegisterTempVar> registerNodes;

  LinkedHashSet<String> colors = new LinkedHashSet(
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9',  'r10', 'r11', 'r12', 'r13', 'r14', 'r15'])

  public RegisterAllocator(MethodDescriptor md) {
    assert(md)
    methodDesc = md
    ig = null;
  }

  void RunRegAllocToFixedPointAndColor() {
    while(RegAllocationIteration());

    DoColoring();
  }

  void DoColoring() {
    // Build the map from tmpVar to color
    tmpVarToRegTempVar = [:]
    ig.BuildNodeToColorNodeMap();
    ig.variables().each { v -> 
      tmpVarToRegTempVar[(tv)] = RegColor.getRegColorFromName(ig.GetColoringNode(v).color);
    }

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      node.SwapUsesUsingMap(tmpVarToRegTempVar)
      node.SwapDefUsingMap(tmpVarToRegTempVar);
    }
  }

  boolean OnlySigDegOrMoveRelated() {
    assert ig; assert ig.nodes;
    ig.nodes.each { node -> 
      if(!(ig.isSigDeg(node) || node.isMovRelated()))
        return false;
    }
    return true;
  }

  // Run this function to fixed point. It implements the flow-diagram in MCIJ in 11.2
  boolean RegAllocationIteration() {
    dbgOut "Now running an iteration of the register allocator."
    Build();

    spillWorklist = new LinkedHashSet<InterferenceNode>();

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

    ig.nodes.each { println it }
    ig.edges.each { println it }
    assert(!OnlySigDegOrMoveRelated())
    ig.nodes.each { assert it.isMoveRelated() == false }

    if(!Select()) {
      Spill();
      return true;
    }

    return false;
  }

  void Build() {
    dbgOut "Now running Build()."

    ig = new InterferenceGraph(methodDesc);
    println "created the new interference graph."

    RegColor.eachRegColor { rc -> 
      ig.addNode(new InterferenceNode(rc.getRegTempVar())); 
    }
    ig.BuildNodeToColoringNodeMap()
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrMov) {
        ig.GetColoringNode(node.src).AddMovRelation(node.dst);
        ig.GetColoringNode(node.dst).AddMovRelation(node.src);
      }
    }
    dbgOut "Finished running Build()."
  }

  void AddNodeToColoringStack(InterferenceNode node) {
    assert false;
    // 1. Remove it from ig.
    // 2. Push it onto the coloringStack.
  }

  boolean Simplify() {
    dbgOut "Now running Simplify()."

    // Determine is there is a non-move-related node of low (< K) degree in the graph.
    InterferenceNode nodeToSimplify = ig.nodes.find { cn -> 
      if(cn.isMovRelated() && cn.isSigDeg())
        return false;
      else if(cn.representative instanceof RegisterTempVar)
        return false;
      return true;
    }

    if(nodeToSimplify == null) {
      dbgOut "Could not find a node to simplify."
      return false;
    } else {
      AddNodeToColoringStack(nodeToSimplify);
      dbgOut "Simplify() removed a node: $nodeToSimplify."
      return true;
    }
  }

  boolean Coalesce() {
    dbgOut "Now running Coalescing."

    // Perform conservative coalescing. Nothing fancy here.
    for(pair in [ig.nodes, ig.nodes].combinations()) { 
      if(pair[0] != pair[1])
        if(ig.CanCoalesceNodes(pair[0], pair[1])) {
          dbgOut "Found a pair of nodes to coalesce: $pair"
          ig.CoalesceNodes(pair[0], pair[1]);
          return true;
        }
    }

    dbgOut "Finished trying to coalesce (no more coalesces found!)."
    return false;
  }

  boolean Freeze() {
    dbgOut "Now running Freeze()."

    ig.nodes.each { n -> 
      if(n.isMovRelated() && !ig.isSigDeg()) {
        // Found a freeze-able node.
        dbgOut "Foudn a freeze-able node: $n"
        n.Freeze();
        ig.BuildNodeToColoringNodeMap();
        n.movRelatedNodes.each { mrn -> 
          GetColoringNode(mrn).RemoveMovRelation(n.representative)
        }
        assert n.movRelatedNodes.size() == 0;
        n.movRelatedNodes = new LinkedHashSet();
        return true;
      }
    }

    dbgOut "No freeze-able nodes found."
    return false;
  }

  boolean PotentialSpill() {
    dbgOut "Now calculating potential spills."

    // need to check that there are no low-degree 
    ig.nodes.each { node -> 
      if(ig.isSigDeg(node)) {
        dbgOut "Found potential spill: $node"
        spillWorklist << node;
        AddNodeToColoringStack(node);
        return true;
      }
    }
    
    dbgOut "Did not find any potential spills."
    return false
  }

  boolean PopNodeAndTryToColor() {
    // Note here, or in Select() (or both), we need to add code that tries 
    // to color (a b -> x) such that b = x. Specifically, consider 
    // add src, dest. Then this is (src dest -> dest).
    assert false;
  }

  boolean Select() {
    dbgOut "Now running select." 

    // Need to pop each node off the stack and try and color it.
    while(coloringStack.size() > 0) {
      if(!PopNodeAndTryToColor())
        return false;
    }

    assert spillWorklist.size() == 0;
    return true;
  }

  InterferenceNode SelectNodeToSpill() {
    // Obviously you want something better than this.
    spillWorklist.asList().first();
  }

  void Spill() {
    InterferenceNode spilledNode = SelectNodeToSpill();
    assert ig.isSigDeg(spilledNode)
    dbgOut "Now handling a spilled node: $spilledNode"

    SpillVar sv = methodDesc.svManager.requestNewSpillVar();

    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      // Determine if this node uses the spilled var.
      node.getUses().intersect(spilledNode.nodes).each { use -> 
        TempVar tv = methodDesc.tempFactory.createLocalTemp();
        node.SwapUsesUsingMap([(use) : tv])
        PlaceSpillLoadBeforeNode(node, sv, tv);
      }

      // Determine if this node defines the spilled var.
      if(spilledNode.nodes.contains(node.getDef())) { 
        TempVar tv = methodDesc.tempFactory.createLocalTemp();
        node.SwapDefUsingMap([(node.getDef()) : tv])
        PlaceSpillStoreAfterNode(node, sv, tv);
      }
    }

    dbgOut "Finished spilling the node."
  }

  void PlaceSpillStoreAfterNode(LowIrNode siteOfSpill, SpillVar sv, TempVar tv) {
    assert siteOfSpill; assert tv; assert sv;
    assert siteOfSpill.getSuccessors().size() == 1;

    LowIrLoadSpill nodeToPlace = new LowIrStoreSpill(tmpVar : tv, loadLoc : sv);
    
    LowIrNode.link(siteOfSpill, nodeToPlace);
    LowIrNode.link(nodeToPlace, siteOfSpill.getSuccessors().first())
    LowIrNode.unlink(siteOfSpill, siteOfSpill.getSuccessors().first())
  }

  void PlaceSpillLoadBeforeNode(LowIrNode siteOfSpill, SpillVar sv, TempVar tv) {
    assert siteOfSpill; assert tv; assert sv;
    assert siteOfSpill.getPredecessors().size() > 0;

    LowIrStoreSpill nodeToPlace = new LowIrStoreSpill(value : tv, storeLoc : sv);
    
    LowIrNode.link(nodeToPlace, siteOfSpill);
    def pred = siteOfSpill.getPredecessors();
    pred.each { p -> 
      LowIrNode.unlink(p, siteOfSpill);
      LowIrNode.link(p, nodeToPlace);
      if(p instanceof LowIrCondJump) {
        if(p.trueDest == siteOfSpill) 
          p.trueDest = nodeToPlace;
        if(p.falseDest == siteOfSpiill)
          p.falseDest = nodeToPlace;
      }
    }
  }
}

public enum RegColor {
  RAX('rax'), RBX('rbx'), RCX('rcx'), RDX('rdx'), RSI('rsi'), 
  RDI('rdi'), R8('r8'),   R9('r9'),   R10('r10'), R11('r11'),
  R12('r12'), R13('r13'), R14('r14'), R15('r15');

  private final String strRegColor;
  private final RegisterTempVar rtv;
  private final Operand operand;
  static LinkedHashMap regNameToRegColor;
  final List<RegColor> callerSaveRegisters;
  final List<RegColor> calleeSaveRegisters;
  final List<RegColor> parameterRegisters;

  RegColor(String strRegColor) {
    assert ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
            'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15'].contains(strRegColor)
    this.strRegColor = strRegColor;
    this.rtv = new RegisterTempVar(strRegColor);
    
    operand = new Operand(strRegColor);
    callerSaveRegisters = [RCX, RDX, RSI, RDI, R8, R9, R10, R11];
    calleeSaveRegisters = [RBX, R12, R13, R14, R15];
    parameterRegisters = [RDI, RSI, RDX, RCX, R8, R9];
  }

  String getRegName() { 
    assert strRegColor;
    return strRegColor; 
  }

  String toString() { 
    assert this.strRegColor
    return this.strRegColor 
  }

  RegisterTempVar getRegTempVar() { 
    assert this.rtv
    return this.rtv; 
  }

  Operand getOperand() { 
    return this.operand 
  }

  static RegColor getRegOfParamArgNum(int argNum) {
    switch(argNum) {
    case 1: return RegColor.RDI;
    case 2: return RegColor.RSI;
    case 3: return RegColor.RDX;
    case 4: return RegColor.RCX;
    case 5: return RegColor.R8;
    case 6: return RegColor.R9;
    default: assert false;
    }
  }

  static RegColor getReg(String regName) {
    for(rc in RegColor.values()) {
      assert rc instanceof RegColor
      if(regName == rc.strRegColor)
        return rc;
    }

    assert false;
  }

  static def eachRegColor = { c -> 
    assert c; 
    return RegColor.values().collect { 
      assert it instanceof RegColor;
      c(it)
    }
  }
}
