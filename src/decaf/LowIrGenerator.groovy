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
  
  LowIrBridge destruct(BooleanLiteral lit) {
    def boolLit = new LowIrIntLiteral(value: lit.value ? 1 : 0, tmpVar: lit.tmpVar)
    def bridge = new LowIrValueBridge(boolLit)
    return bridge
  }

  LowIrBridge destruct(BinOp binop) {
    def leftBridge = destruct(binop.left)
    def rightBridge = destruct(binop.right)
    def lowirBinop = new LowIrBinOp(leftTmpVar: leftBridge.tmpVar, rightTmpVar: rightBridge.tmpVar, tmpVar: binop.tmpVar, op: binop.op)
    leftBridge = leftBridge.seq(rightBridge)
    return leftBridge.seq(new LowIrValueBridge(lowirBinop))
  }

  LowIrBridge destruct(IfThenElse ite) {
    def thenBridge = destruct(ite.thenBlock)
    def elseBridge;

    // This code handles the situation where there is no else block
    if(ite.elseBlock) {
      elseBridge = destruct(ite.elseBlock)
    } else {
      // This is the noOp version
      elseBridge = new LowIrBridge(new LowIrNode(), new LowIrNode())
    }

    // Get the short circuited bridge for the conditional expression
    def condBridge = destructShortCircuit(ite.condition, thenBridge.begin, elseBridge.begin)

    // But the cond bridge doesn't handle jumping if no short-circuiting happened 
    // (the tail of the returned bridge)
    println "thenBridge.begin = ${thenBridge.begin}"
    println "elseBridge.begin = ${elseBridge.begin}"
    def jmpNode = new LowIrJump(tmpVar:condBridge.end.tmpVar, jmpTrueDest: thenBridge.begin, jmpFalseDest: elseBridge.begin)

    // Glue the end of the condBridge to the final jmpNode
    LowIrNode.link(condBridge.end, jmpNode)

    // Glue the final jmpNode to the then and else bridges.
    LowIrNode.link(jmpNode, thenBridge.begin)
    LowIrNode.link(jmpNode, elseBridge.begin)

    // Now glue the ends of the then and else bridge to a common exit node.
    def exitNode = new LowIrNode()
    LowIrNode.link(thenBridge.end, exitNode)
    LowIrNode.link(elseBridge.end, exitNode)

    return new LowIrBridge(condBridge.begin, exitNode);
  }

  LowIrBridge destructShortCircuit(BooleanLiteral bool, LowIrNode thenBlockEntry, LowIrNode elseBlockEntry){
    if(bool.value == true){
      def trueNode = new LowIrValueNode(tmpVar:bool.tmpVar)
      def trueBridge = new LowIrValueBridge(trueNode)
      return trueBridge
    } else {
      def falseNode = new LowIrValueNode(tmpVar:bool.tmpVar)
      def falseBridge = new LowIrValueBridge(falseNode)
      return falseBridge
    }
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
        // if the returned value is false, then short circuit.
        // short circuit by jumping to the else-block.
        jmpNode = new LowIrJump(jmpTrueDest: rightBridge.begin, jmpFalseDest: elseBlockEntry)
        LowIrNode.link(jmpNode, elseBlockEntry)
      } else {
        // binop.op is BinOpType.OR
        // if the returned value is true, then short circuit.
        // short circuit by jumping to the then-block.
        jmpNode = new LowIrJump(jmpTrueDest: thenBlockEntry, jmpFalseDest: rightBridge.begin)
        LowIrNode.link(jmpNode, thenBlockEntry)
      }

      // left_bridge -> jmpNode -> rightBridge
      LowIrNode.link(leftBridge.end, jmpNode)
      LowIrNode.link(jmpNode, rightBridge.begin)

      return new LowIrBridge(leftBridge.begin, rightBridge.end)
    } else {
      return destruct(binop);
    }

    // should never reach here!
    assert(false);
  }

  LowIrBridge destruct(ForLoop forloop) {
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
