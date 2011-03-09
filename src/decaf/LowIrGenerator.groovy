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
    def paramValues = params.collect { it.value }
    def lowir = new LowIrCallOut(name: callout.name.value, params: paramValues)
    return bridge.seq(new LowIrBridge(lowir))
  }

  LowIrBridge destruct(StringLiteral lit) {
    def strlit = new LowIrStringLiteral(value: lit.value, tmpNum: lit.tmpNum)
    def bridge = new LowIrValueBridge(strlit)
    bridge.value = strlit
    return bridge
  }

  LowIrBridge destruct(IntLiteral lit) {
    def intlit = new LowIrIntLiteral(value: lit.value, tmpNum: lit.tmpNum)
    def bridge = new LowIrValueBridge(intlit)
    bridge.value = intlit
    return bridge
  }
}
