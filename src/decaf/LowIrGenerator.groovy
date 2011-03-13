package decaf
import static decaf.BinOpType.*
import static decaf.LowIrNode.link

class LowIrGenerator {

  LowIrBridge destruct(Block block) {
    def internals = block.statements.collect { destruct(it) }
    def bridge = new LowIrBridge(new LowIrNode())
    internals.each {
      bridge = bridge.seq(it)
    }
    return bridge
  }

  LowIrBridge destruct(CallOut callout) {
    def bridge = new LowIrBridge(new LowIrNode())
    // BUG? Shouldn't these be written from right to left, not left
    // to right? We have to follow the calling convention.

    // params: List<LowIrValueBridge>
    def params = callout.params.collect { destruct(it) }
    params.each {
      bridge = bridge.seq(it)
    }
    //def paramNums = params.collect { it.tmpVar }
    def paramTmpVars = params.collect { it.tmpVar }
    //def lowir = new LowIrCallOut(name: callout.name.value, paramNums: paramNums)
    def lowir = new LowIrCallOut(name: callout.name.value, paramTmpVars: paramTmpVars)
    return bridge.seq(new LowIrBridge(lowir))
  }

  LowIrBridge destruct(MethodCall methodCall) {
    def bridge = new LowIrBridge(new LowIrNode())
    def params = methodCall.params.collect { destruct(it) }
    params.each {
      bridge = bridge.seq(it)
    }
    def lowir = new LowIrMethodCall(descriptor: methodCall.descriptor)
    return bridge.seq(new LowIrBridge(lowir))
  }

  LowIrBridge destruct(StringLiteral lit) {
    //def strlit = new LowIrStringLiteral(value: lit.value, tmpNum: lit.tmpVar)
    //println "lit.tmpNum = $lit.tmpNum, strlit.tmpNum = $strlit.tmpNum"
    def strlit = new LowIrStringLiteral(value: lit.value, tmpVar: lit.tmpVar)
    println "lit.tmpVar.getId() = ${lit.tmpVar.getId()}, strlit.tmpVar.getId() = ${strlit.tmpVar.getId()}"
    
    def bridge = new LowIrValueBridge(strlit)
    return bridge
  }

  LowIrBridge destruct(IntLiteral lit) {
    // def intlit = new LowIrIntLiteral(value: lit.value, tmpNum: lit.tmpNum)
    // println "lit.tmpNum = $lit.tmpNum, intlit.tmpNum = $intlit.tmpNum"
    def intlit = new LowIrIntLiteral(value: lit.value, tmpVar: lit.tmpVar)
    println "lit.tmpVar.getId() = ${lit.tmpVar.getId()}, intlit.tmpVar.getId() = ${intlit.tmpVar.getId()}"
    
    def bridge = new LowIrValueBridge(intlit)
    return bridge
  }

  // Nathan note:
  // No LowIrBridge that accepts an Assignment!

  LowIrBridge destruct(BinOp binop) {
    def leftBridge = destruct(binop.left)
    def rightBridge = destruct(binop.right)

    // def lowirBinop = new LowIrBinOp(leftTmpNum: leftBridge.tmpNum, rightTmpNum: rightBridge.tmpNum, tmpNum: binop.tmpNum, op: binop.op)
    def lowirBinop = new LowIrBinOp(leftTmpVar: leftBridge.tmpVar, rightTmpVar: rightBridge.tmpVar, tmpVar: binop.tmpVar, op: binop.op)
    leftBridge = leftBridge.seq(rightBridge)
    return leftBridge.seq(new LowIrValueBridge(lowirBinop))
  }

  LowIrBridge destruct(IfThenElse ite) {
    def thenBridge = destruct(ite.thenBlock)
    def elseBridge;

    // This code handles the situation where there is no else block
    if(ite.elseBlock)
      elseBridge = destruct(ite.elseBlock)
    else
      elseBridge = new LowIrBridge(new LowIrNode(), new LowIrNode())

    // Get the short circuited bridge for the conditional expression
    def condBridge = destructShortCircuit(ite.condition, thenBridge.begin, elseBridge.begin)

    // But the cond bridge doesn't handle jumping if no short-circuiting happened 
    // (the tail of the returned bridge)
    def jmpNode = new LowIrJump(jmpTrueDest: thenBridge.begin, jmpFalseDest: elseBridge.begin)
    
    condBridge = condBride.seq(new LowIrValueBridge(jmpNode))
    
    // Now glue the ends of the then and else bridge to a common exit node.
    def exitNode = new LowIrNode()
    LowIrNode.link(thenBridge.end, exitNode)
    LowIrNode.link(elseBridge.end, exitNode)

    return new LowIrBridge(condBridge.begin, exitNode);
  }

  LowIrBridge destructShortCircuit(BinOp binop, LowIrNode thenBlockEntry, LowIrNode elseBlockEntry) {
    if(binop.op == BinOpType.AND || binop.op == BinOpType.OR) {
      // Short circuiting required!
      // Compute the left sub-expression
      def leftBridge = destructShortCircuit(binop.left, thenBlockEntry, elseBlockEntry)
      
      // Compute the right sub-expression
      def rightBridge = destructShortCircuit(binop.right, thenBlockEntry, elseBlockEntry)

      def jmpNode;

      if(binop.op == BinOpType.AND) {
        // if the returned value is false, then short circuit and 
        // jump to the else Block. 
        jmpNode = new LowIrJump(jmpTrueDest: rightBridge.begin, jmpFalseDest: elseBlockEntry)
        LowIrNode.link(jmpNode, elseBlockEntry)
      } else {
        // Short circuit by jumping to the then Block. Jump if true.
        jmpNode = new LowIrJump(jmpTrueDest: thenBlockEntry, jmpFalseDest: rightBridge.begin)
        LowIrNode.link(jmpNode, thenBlockEntry)
      }

      LowIrNode.link(leftBridge.begin, jmpNode)
      LowIrNode.link(jmpNode, rightBridge.begin)

      return new LowIrBridge(leftBridge.begin, rightBridge.end)
    } else {
      return destruct(binop);
    }

    // should never reach here!
    assert(false);
  }
}
