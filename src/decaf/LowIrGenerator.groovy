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
    def paramTmpVars = params.collect { it.tmpVar }
    def lowir = new LowIrCallOut(name: callout.name.value, paramTmpVars: paramTmpVars)
    return bridge.seq(new LowIrBridge(lowir))
  }

  LowIrBridge destruct(MethodCall methodCall) {
    def bridge = new LowIrValueBridge(new LowIrValueNode())
    def params = methodCall.params.collect { destruct(it) }
    params.each {
      bridge = bridge.seq(it)
    }
    def paramTmpVars = params.collect { it.tmpVar }
    def lowir = new LowIrMethodCall(descriptor: methodCall.descriptor, paramTmpVars: paramTmpVars)
    lowir.tmpVar = methodCall.tmpVar
    return bridge.seq(new LowIrValueBridge(lowir))
  }

  LowIrBridge destruct(Return ret) {
    def bridge = new LowIrBridge(new LowIrNode())
    def lowir = new LowIrReturn()
    if (ret.expr != null) {
      bridge = destruct(ret.expr)
      lowir.tmpVar = bridge.tmpVar
    }
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

  LowIrBridge destruct(Location loc) {
    //TODO: handle arrays
    return new LowIrValueBridge(new LowIrValueNode(tmpVar: loc.descriptor.tmpVar))
  }
}
