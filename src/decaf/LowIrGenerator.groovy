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
    def params = callout.params.collect { destruct(it) }
    params.each {
      bridge = bridge.seq(it)
    }
    def paramNums = params.collect { it.tmpNum }
    def lowir = new LowIrCallOut(name: callout.name.value, paramNums: paramNums)
    return bridge.seq(new LowIrBridge(lowir))
  }

  LowIrBridge destruct(StringLiteral lit) {
    def strlit = new LowIrStringLiteral(value: lit.value, tmpNum: lit.tmpNum)
    println "lit.tmpNum = $lit.tmpNum, strlit.tmpNum = $strlit.tmpNum"
    def bridge = new LowIrValueBridge(strlit)
    return bridge
  }

  LowIrBridge destruct(IntLiteral lit) {
    def intlit = new LowIrIntLiteral(value: lit.value, tmpNum: lit.tmpNum)
    println "lit.tmpNum = $lit.tmpNum, intlit.tmpNum = $intlit.tmpNum"
    def bridge = new LowIrValueBridge(intlit)
    return bridge
  }

  LowIrBridge destruct(BinOp binop) {
    def leftBridge = destruct(binop.left)
    def rightBridge = destruct(binop.right)
    def lowirBinop = new LowIrBinOp(leftTmpVar: leftBridge.tmpVar, rightTmpVar: rightBridge.tmpVar, tmpNum: binop.tmpVar, op: binop.op)
    leftBridge = leftBridge.seq(rightBridge)
    return leftBridge.seq(new LowIrValueBridge(lowirBinop))
  }

  LowIrBridge destruct(IfThenElse ite) {
    // Here we need to handle short circuiting!
    def condExpr = ite.condition
    if(condExpr instanceof 
    
    def thenBridge = destruct(ite.thenBlock)
    def elseBridge = destruct(ite.elseBlock)

    // Get the short circuited bridge for the conditional expression
    def condBridge = destructShortCircuit(ite.condition, thenBridge.begin, elseBridge.begin)

    // Now glue the ends of the then and else bridge to a common exit node.
    def exitNode = new LowIrNode()
    LowIrNode.link(thenBridge.end, exitNode)
    LowIrNode.link(elseBridge.end, exitNode)

    return new LowIrBridge(condBridge.begin, exitNode);
  }

  LowIrBridge destructShortCircuit(BinOp binop, LowIrNode trueBlockEntry, LowIrNode falseBlockEntry) {
    if(binop.op == BinOpType.AND || binop.op == BinOpType.OR) {
      // Short circuiting required!
      // Compute the left sub-expression
      def leftBridge = destructShortCircuit(binop.left, trueBlockEntry, falseBlockEntry)
      
      // Compute the right sub-expression
      def rightBridge = destructShortCircuit(binop.right, trueBlockEntry, falseBlockEntry)

      // If the left sub-expression was false, short-circuit and go to false block
      def jmpNode;

      if(binop.op == BinOpType.AND) {
        jmpNode = new LowIrJump(jumpNode: falseBlockEntry)
        LowIrNode.link(jmpNode, falseBlockEntry)
      } else {
        jmpNode = new LowIrJump(jumpNode: trueBlockEntry)
        LowIrNode.link(jmpNode, trueBlockEntry)
      }
      
      def jumpBridge = null new LowIrJumpBridge(jmpNode)

      // Join the three bridges together and return them.
      return leftBridge.seq(jumpBridge.seq(rightBridge.seq))
    } else {
      return destruct(binop);
    }

    // should never reach here!
    assert(false);
  }
}
