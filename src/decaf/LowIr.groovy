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
}

class LowIrCallOut extends LowIrValueNode {
  String name
  TempVar[] paramTmpVars

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
}


class LowIrMethodCall extends LowIrValueNode {
  MethodDescriptor descriptor
  TempVar[] paramTmpVars

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
}

class LowIrStringLiteral extends LowIrValueNode {
  String value

  String toString() {
    "LowIrStringLiteral(value: $value, tmpVar: $tmpVar)"
  }
}

class LowIrIntLiteral extends LowIrValueNode {
  int value

  String toString() {
    "LowIrIntLiteral(value: $value, tmpVar: $tmpVar)"
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
}
