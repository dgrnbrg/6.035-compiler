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
    def lowirBinop = new LowIrBinOp(leftTmpNum: leftBridge.tmpNum, rightTmpNum: rightBridge.tmpNum, tmpNum: binop.tmpNum)
    leftBridge = leftBridge.seq(rightBridge)
    return leftBridge.seq(new LowIrValueBridge(lowirBinop))
  }
}
