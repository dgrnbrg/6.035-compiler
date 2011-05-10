package decaf
import decaf.graph.*

class LowIr {}

class LowIrBridge {
  LowIrNode begin, end

  LowIrBridge(LowIrNode node) {
    begin = node
    end = node
  }

  LowIrBridge(LowIrNode begin, LowIrNode end) {
    this.begin = begin
    this.end = end
  }

  LowIrBridge(List nodes) {
    if (nodes.isEmpty()) {
      this.begin = new LowIrNode(metaText:'empty block')
      this.end = this.begin
      return
    }
    assert nodes.inject(true){cond, node -> cond && node instanceof LowIrNode} ||
           nodes.inject(true){cond, node -> cond && node instanceof LowIrBridge}
    if (nodes[0] instanceof LowIrNode) {
      this.begin = nodes[0]
      this.end = nodes[-1]
      for (int i = 0; i < nodes.size() - 1; i++) {
        LowIrNode.link(nodes[i], nodes[i+1])
      }
    } else if (nodes[0] instanceof LowIrBridge) {
      nodes.eachWithIndex { it, index ->
        if (index == 0) return
        LowIrNode.link(nodes[index-1].end, it.begin)
      }
      this.begin = nodes[0].begin
      this.end = nodes[-1].end
    }
  }

  LowIrBridge seq(LowIrBridge next) {
    LowIrNode.link(this.end, next.begin)
    if (next instanceof LowIrValueBridge) {
      return new LowIrValueBridge(this.begin, next.end)
    } else {
      return new LowIrBridge(this.begin, next.end)
    }
  }

  void insertBetween(LowIrNode before, LowIrNode after) {
    LowIrNode.unlink(before, after)
    LowIrNode.link(before, begin)
    LowIrNode.link(end, after)
    //fix the explicit links in Cond Jumps
    if (before instanceof LowIrCondJump) {
      if (before.trueDest == after) {
        before.trueDest = begin
      } else if (before.falseDest == after) {
        before.falseDest = begin
      } else {
        assert false
      }
    }
  }

  void insertBefore(LowIrNode node) {
    if (node.predecessors.size() > 1) {
      def noop = new LowIrNode(metaText: 'insertBefore cruft')
      node.predecessors.clone().each {
        LowIrNode.unlink(it, node)
        LowIrNode.link(it, noop)
        if (it instanceof LowIrCondJump) {
          if (it.trueDest == node) {
            it.trueDest = noop
          } else if (it.falseDest == node) {
            it.falseDest = noop
          } else {
            assert false
          }
        }
      }
      LowIrNode.link(noop, node)
    }
    insertBetween(node.predecessors[0], node)
  }

  //removes this bridge from the lowir
/*
  void excise() {
    assert end.successors.size() <= 1
    def successors = end.successors.clone()
    def predecessors = begin.predecessors.clone()
    predecessors.each {
      LowIrNode.unlink(it, begin)
      LowIrNode.link(it, )//here
    }
    LowIrNode.
  }
*/
}

class LowIrValueBridge extends LowIrBridge {
  TempVar tmpVar

  LowIrValueBridge(LowIrValueNode node) {
    super(node)
    tmpVar = node.tmpVar
  }

  LowIrValueBridge(LowIrNode begin, LowIrValueNode end) {
    super(begin, end)
    tmpVar = end.tmpVar
  }
}

class LowIrNode implements GraphNode{
  def anno = [:]

  def predecessors = []
  def successors = []

  def metaText = ''
  def frak = false

  static int labelNum = 0
  def label = 'label'+(labelNum++)

  List getPredecessors() { predecessors }
  List getSuccessors() { successors }

  static void link(LowIrNode fst, LowIrNode snd) {
    fst.successors << snd
    snd.predecessors << fst
  }

  static void unlink(LowIrNode fst, LowIrNode snd) {
    assert fst.successors.contains(snd)
    assert snd.predecessors.contains(fst)
    fst.successors.remove(snd)
    snd.predecessors.remove(fst)
  }

  String toString() {
    "LowIrNode($metaText)"
  }

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    return 0
  }

  //returns true if a definition was replaced
  boolean replaceDef(TempVar oldVar, TempVar newVar) {
    return false
  }

  TempVar getDef() {
    return null
  }

  Collection<TempVar> getUses() {
    return []
  }

  void SwapUsesUsingMap(mapToApply) { 
    assert mapToApply != null
  }

  void SwapDefUsingMap(mapToApply) { 
    assert mapToApply != null
  }

  void excise() {
    if (successors.size() == 1) {
      def oldPreds = predecessors.clone()
      def oldSucc = successors[0]
      unlink(this, oldSucc)
      for (pred in oldPreds) {
        unlink(pred, this)
        link(pred, oldSucc)
        //fix the explicit links in Cond Jumps
        if (pred instanceof LowIrCondJump) {
          if (pred.trueDest == this) {
            pred.trueDest = oldSucc
          } else if (pred.falseDest == this) {
            pred.falseDest = oldSucc
          } else {
            assert false
          }
        }
      }
      if (getDef()) {
        getDef().defSite = null
      }
      for (use in getUses()) {
        use.useSites.remove(this)
      }
    }
  }
}

class LowIrCondJump extends LowIrNode {
  TempVar condition
  LowIrNode trueDest, falseDest

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    if (condition == oldVar) {
      condition = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }

  Collection<TempVar> getUses() {
    return [condition]
  }

  String toString() {
    "LowIrCondJump(condition: $condition)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null;
    if(mapToApply[(condition)]) 
      condition = mapToApply[(condition)]
  }
}

class LowIrCondCoalesced extends LowIrCondJump {
  TempVar leftTmpVar
  TempVar rightTmpVar
  BinOpType op

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    assert oldVar != null
    if (leftTmpVar == oldVar) {
      leftTmpVar = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    if (rightTmpVar == oldVar) {
      rightTmpVar = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }

  Collection<TempVar> getUses() {
    return [leftTmpVar, rightTmpVar]
  }

  String toString() {
    "LowIrCondCoalesced(op: $op, leftTmp: $leftTmpVar, rightTmp: $rightTmpVar)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null; assert leftTmpVar;
    if(mapToApply[(leftTmpVar)])
      leftTmpVar = mapToApply[(leftTmpVar)]
    if(rightTmpVar && mapToApply[(rightTmpVar)])
      rightTmpVar = mapToApply[(rightTmpVar)]
  }
}

class LowIrCallOut extends LowIrValueNode {
  String name
  TempVar[] paramTmpVars
  int numOriginalArgs; // <-- used by reg alloc.

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    paramTmpVars.eachWithIndex { var, index ->
      if (var == oldVar) {
        paramTmpVars[index] = newVar
        oldVar.useSites.remove(this)
        newVar.useSites << this
        x++
      }
    }
    return x
  }

  Collection<TempVar> getUses() {
    return paramTmpVars.clone()
  }

  String toString() {
    "LowIrCallOut(method: $name, tmpVar: $tmpVar, params: $paramTmpVars)"
  }

  void SwapUsesUsingMap(mapToApply) {
    paramTmpVars = paramTmpVars.collect { tv -> 
      return (mapToApply[(tv)] ? mapToApply[(tv)] : tv)
    }
  }
}


class LowIrMethodCall extends LowIrValueNode {
  MethodDescriptor descriptor
  TempVar[] paramTmpVars
  int numOriginalArgs; // <-- used by reg alloc

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    paramTmpVars.eachWithIndex { var, index ->
      if (var == oldVar) {
        paramTmpVars[index] = newVar
        oldVar.useSites.remove(this)
        newVar.useSites << this
        x++
      }
    }
    return x
  }

  Collection<TempVar> getUses() {
    return paramTmpVars.clone()
  }

  String toString() {
    "LowIrMethodCall(method: $descriptor.name, tmpVar: $tmpVar, params: $paramTmpVars)"
  }

  void SwapUsesUsingMap(mapToApply) {
    paramTmpVars = paramTmpVars.collect { tv -> 
      return (mapToApply[(tv)] ? mapToApply[(tv)] : tv)
    }
  }
}

class LowIrReturn extends LowIrNode {
  TempVar tmpVar

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    if (tmpVar == oldVar) {
      tmpVar = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }

  //returns true if a definition was replaced
  boolean replaceDef(TempVar oldVar, TempVar newVar) {
    return false
  }

  Collection<TempVar> getUses() {
    return tmpVar ? [tmpVar] : []
  }

  String toString() {
    "LowIrReturn(tmpVar: $tmpVar)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null
    if(mapToApply[(tmpVar)])
      tmpVar = mapToApply[(tmpVar)]
  }
}

class LowIrValueNode extends LowIrNode{
  TempVar tmpVar

  //returns true if a definition was replaced
  boolean replaceDef(TempVar oldVar, TempVar newVar) {
    if (tmpVar == oldVar) {
      tmpVar = newVar
      oldVar.defSite = null
      newVar.defSite = this
      return true
    }
    return false
  }

  TempVar getDef() {
    if (this.getClass() != LowIrValueNode.class) {
      return tmpVar
    }
    return null
  }

  String toString() {
    "LowIrValueNode($metaText, tmpVar: $tmpVar)"
  }

  void SwapDefUsingMap(mapToApply) {
    assert mapToApply != null
    if(mapToApply[(tmpVar)])
      tmpVar = mapToApply[(tmpVar)]
  }
}

class LowIrStringLiteral extends LowIrValueNode {
  String value

  String toString() {
    "LowIrStringLiteral(value: $value, tmpVar: $tmpVar)"
  }
}

class LowIrIntLiteral extends LowIrValueNode {
  int value
  boolean useless = false; // <-- used by regalloc

  String toString() {
    "LowIrIntLiteral(value: $value, tmpVar: $tmpVar)"
  }
}

class LowIrRightCurriedOp extends LowIrValueNode {
  TempVar input
  int constant
  BinOpType op

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    assert oldVar != null
    int x = 0
    if (input == oldVar) {
      input = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }
  
  Collection<TempVar> getUses() {
    return [input]
  }

  String toString() {
    "LowIrRightCurriedOp(op: $op, input: $input, const: $constant, tmpVar: $tmpVar)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null; assert input;
    if(mapToApply[(input)])
      input = mapToApply[(input)]
  }
}

class LowIrLeftCurriedOp extends LowIrValueNode {
  TempVar input
  int constant
  BinOpType op

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    assert oldVar != null
    int x = 0
    if (input == oldVar) {
      input = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }
  
  Collection<TempVar> getUses() {
    return [input]
  }

  String toString() {
    "LowIrLeftCurriedOp(op: $op, input: $input, const: $constant, tmpVar: $tmpVar)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null; assert input;
    if(mapToApply[(input)])
      input = mapToApply[(input)]
  }
}

class LowIrBinOp extends LowIrValueNode {
  TempVar leftTmpVar, rightTmpVar
  BinOpType op

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    assert oldVar != null
    int x = 0
    if (leftTmpVar == oldVar) {
      leftTmpVar = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    if (rightTmpVar == oldVar) {
      rightTmpVar = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }
  
  Collection<TempVar> getUses() {
    return rightTmpVar ? [leftTmpVar, rightTmpVar] : [leftTmpVar]
  }

  String toString() {
    "LowIrBinOp(op: $op, leftTmp: $leftTmpVar, rightTmp: $rightTmpVar, tmpVar: $tmpVar)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null; assert leftTmpVar;
    if(mapToApply[(leftTmpVar)])
      leftTmpVar = mapToApply[(leftTmpVar)]
    if(rightTmpVar && mapToApply[(rightTmpVar)])
      rightTmpVar = mapToApply[(rightTmpVar)]
  }
}

class LowIrMov extends LowIrNode {
  TempVar src, dst

  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    if (src == oldVar) {
      src = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }

  //returns true if a definition was replaced
  boolean replaceDef(TempVar oldVar, TempVar newVar) {
    if (dst == oldVar) {
      dst = newVar
      oldVar.defSite = null
      newVar.defSite = this
      return true
    }
    return false
  }

  Collection<TempVar> getUses() {
    return [src]
  }

  TempVar getDef() {
    return dst
  }

  String toString() {
    "LowIrMov(src: $src, dst: $dst)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null
    if(mapToApply[(src)])
      src = mapToApply[(src)]
  }

  void SwapDefUsingMap(mapToApply) {
    assert mapToApply != null
    if(mapToApply[(dst)])
      dst = mapToApply[(dst)]
  }
}

class LowIrBoundsCheck extends LowIrNode {
  TempVar testVar
  int lowerBound, upperBound
  VariableDescriptor desc

  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    if (testVar == oldVar) {
      testVar = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }

  Collection<TempVar> getUses() {
    return [testVar]
  }

  String toString() {
    "LowIrBoundsCheck($lowerBound <= $testVar < $upperBound for $desc)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null
    if(mapToApply[(testVar)])
      testVar = mapToApply[(testVar)]
  }
}

class LowIrStore extends LowIrNode {
  VariableDescriptor desc
  TempVar index
  TempVar value //this is what gets stored

  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    if (index == oldVar) {
      index = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    if (value == oldVar) {
      value = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }

  Collection<TempVar> getUses() {
    return index ? [index, value] : [value]
  }

  String toString() {
    "LowIrStore(value: $value, desc: $desc, index: $index)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null
    if(mapToApply[(value)])
      value = mapToApply[(value)]
    if(mapToApply[(index)])
      index = mapToApply[(index)]
  }
}

class LowIrCopyArray extends LowIrNode {
  VariableDescriptor src, dst

  String toString() { "LowIrCopyArray($src, $dst)" }
}

class LowIrParallelizedLoop extends LowIrNode {
  MethodDescriptor func
  VariableDescriptor thread0id, thread1id, thread2id, thread3id

  String toString() {
    "LowIrParallelizedLoop($func)"
  }
}

class LowIrLoad extends LowIrValueNode {
  VariableDescriptor desc
  TempVar index

  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    if (index == oldVar) {
      index = newVar
      oldVar.useSites.remove(this)
      newVar.useSites << this
      x++
    }
    return x
  }

  Collection<TempVar> getUses() {
    return index ? [index] : []
  }

  String toString() {
    "LowIrLoad(desc: $desc, index: $index, tmpVar: $tmpVar)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null
    if(mapToApply[(index)])
      index = mapToApply[(index)]
  }
}

class LowIrStoreSpill extends LowIrNode {
  TempVar value // This is what is stored
  SpillVar storeLoc // This is where it is stored

  int replaceUse(TempVar oldVar, TempVar newVar) {
    assert false; // not yet implemented
  }

  Collection<TempVar> getUses() {
    return [value]
  }

  String toString() {
    "LowIrStoreSpill(value: $value, storeLoc: $storeLoc)"
  }

  void SwapUsesUsingMap(mapToApply) {
    assert mapToApply != null
    if(mapToApply[(value)])
      value = mapToApply[(value)]
  }
}

class LowIrLoadSpill extends LowIrValueNode {
  SpillVar loadLoc; // This is where the value is loaded from.
  
  int replaceUse(TempVar oldVar, TempVar newVar) {
    assert false;
  }

  Collection<TempVar> getUses() {
    return []
  }

  String toString() {
    "LowIrLoadSpill(tmpVar: $tmpVar, loadLoc: $loadLoc)"
  }
}

class LowIrLoadArgOntoStack extends LowIrNode {
  TempVar arg;
  int paramNum; // Indexing starts at 1, 2, 3, 4, ... for paramNum
  
  LowIrLoadArgOntoStack(TempVar paramArg, int paramPosition) {
    assert paramPosition > 6;
    paramNum = paramPosition;
    arg = paramArg;
  }

  Collection<TempVar> getUses() {
    return [arg];
  }

  String toString() {
    "LowIrLoadArgOntoStack(arg : $arg, paramNum : $paramNum)"
  }

  void SwapUsesUsingMap(mapToApply) {
    if(mapToApply[arg])
      arg = mapToApply[arg]
  }
}

class LowIrPhi extends LowIrValueNode {
  TempVar[] args

  //returns the number of replacements that happened
  int replaceUse(TempVar oldVar, TempVar newVar) {
    int x = 0
    args.eachWithIndex { var, index ->
      if (var == oldVar) {
        args[index] = newVar
        oldVar.useSites.remove(this)
        newVar.useSites << this
        x++
      }
    }
    return x
  }

  Collection<TempVar> getUses() {
    return args.clone()
  }

  String toString() {
    "LowIrPhi(tmpVar: $tmpVar, args: $args)"
  }

  void SwapUsesUsingMap(mapToApply) {
    //assert false
  }

  /*void SwapDefUsingMap(mapToApply) {
    assert false
  }*/
}
