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
    def bridge = new LowIrValueBridge(new LowIrValueNode())
    def params = callout.params.collect { destruct(it) }
    params.each {
      bridge = bridge.seq(it)
    }
    def paramTmpVars = params.collect { it.tmpVar }
    def lowir = new LowIrCallOut(name: callout.name.value, paramTmpVars: paramTmpVars)
    lowir.tmpVar = callout.tmpVar
    return bridge.seq(new LowIrValueBridge(lowir))
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
    def strlit = new LowIrStringLiteral(value: lit.value, tmpVar: lit.tmpVar)
    def bridge = new LowIrValueBridge(strlit)
    return bridge
  }

  LowIrBridge destruct(IntLiteral lit) {
    def intlit = new LowIrIntLiteral(value: lit.value, tmpVar: lit.tmpVar)
    def bridge = new LowIrValueBridge(intlit)
    return bridge
  }

  LowIrBridge destruct(BinOp binop) {
    def leftBridge = destruct(binop.left)
    def rightBridge = destruct(binop.right)
    def lowirBinop = new LowIrBinOp(leftTmpVar: leftBridge.tmpVar, rightTmpVar: rightBridge.tmpVar, tmpVar: binop.tmpVar, op: binop.op)
    leftBridge = leftBridge.seq(rightBridge)
    return leftBridge.seq(new LowIrValueBridge(lowirBinop))
  }

  LowIrValueBridge destructLocation(Location loc) {
    //2 cases: array and scalar
    if (loc.indexExpr == null) {
      return new LowIrValueBridge(new LowIrValueNode(tmpVar: loc.descriptor.tmpVar))
    } else {
      def bridge = destruct(loc.indexExpr)
      def arrTmpVar = new TempVar(TempVarType.ARRAY)
      arrTmpVar.globalName = loc.descriptor.name + '_globalvar'
      arrTmpVar.arrayIndexTmpVar = bridge.tmpVar
      return bridge.seq(new LowIrValueBridge(new LowIrValueNode(tmpVar: arrTmpVar)))
    }
  }

  LowIrBridge destruct(Assignment assignment) {
    def dstBridge = destructLocation(assignment.loc)
    def srcBridge = destruct(assignment.expr)
    def lowir = new LowIrMov(src: srcBridge.tmpVar, dst: dstBridge.tmpVar)
//TODO: david--fix this
/*
class Program {
  int a[10], x;
  int foo() {return x+=1;}
  void main() {
    a[x]+=foo();
    a[foo()] = 1;
  }
}
*/
    return dstBridge.seq(srcBridge).seq(new LowIrBridge(lowir))
  }

  LowIrBridge destruct(Location loc) {
    def bridge = destructLocation(loc)
    def lowir = new LowIrMov(src: bridge.tmpVar, dst: loc.tmpVar)
    def valBridge = new LowIrValueBridge(new LowIrValueNode(tmpVar: loc.tmpVar))
    return bridge.seq(new LowIrBridge(lowir)).seq(valBridge)
  }
}
