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
  ColoringStack theStack;

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
    def tmpVarToRegTempVar = [:]
    ig.BuildNodeToColoringNodeMap();
    ig.nodes.each { node -> 
      node.nodes.each { n ->
        assert node.color;
        assert n instanceof TempVar
        tmpVarToRegTempVar[n] = node.color.getRegTempVar();
      }
    }

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      node.SwapUsesUsingMap(tmpVarToRegTempVar)
      node.SwapDefUsingMap(tmpVarToRegTempVar);
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

    theStack = new ColoringStack(ig);
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

    assert OnlySigDegOrMoveRelated();
    ig.nodes.each { assert it.isMovRelated() == false }

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
      theStack.PushNodeFromGraphToStack(nodeToSimplify);
      dbgOut "Simplify() removed a node: $nodeToSimplify."
      return true;
    }
  }

  boolean Coalesce() {
    dbgOut "Now running Coalescing."

    // Perform conservative coalescing. Nothing fancy here.
    for(pair in [ig.nodes, ig.nodes].combinations()) { 
      if(pair[0] != pair[1])
        if(!(pair[0].representative instanceof RegisterTempVar) && 
            !(pair[1].representative instanceof RegisterTempVar))
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

    for(n in ig.nodes) { 
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
    for(node in ig.nodes) { 
      if(ig.isSigDeg(node)) {
        dbgOut "Found potential spill: $node"
        spillWorklist << node;
        theStack.PushNodeFromGraphToStack(node);
        return true;
      }
    }
    
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
    // Obviously you want something better than this.
    return theStack.Peek().node.representative;
  }

  void Spill() {
    TempVar tempVarToSpill = SelectTempVarToSpill();
    dbgOut "Spilling the TempVar: $tempVarToSpill"

    SpillVar sv = methodDesc.svManager.requestNewSpillVar();

    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      // Determine if this node uses the spilled var.
      if(node.getUses().contains(tempVarToSpill)) {
        TempVar tv = methodDesc.tempFactory.createLocalTemp();
        node.SwapUsesUsingMap([(tempVarToSpill) : tv]);
        PlaceSpillLoadBeforeNode(node, sv, tv);
      }

      // Determine if this node defines the spilled var.
      if(tempVarToSpill == node.getDef()) {
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
