package decaf
import static decaf.BinOpType.*
import static decaf.LowIrNode.link

class LowIrGenerator {

  MethodDescriptor desc //keep a descriptor around to generate new temps or inspect other properties

  LowIrBridge destruct(MethodDescriptor desc) {
    this.desc = desc
    return destruct(desc.block)
  }

  LowIrBridge destruct(Block block) {
    def internals = block.statements.collect { destruct(it) }
    def bridge = new LowIrBridge(new LowIrNode(metaText:'begin block'))
    internals.each {
      bridge = bridge.seq(it)
    }
    return bridge
  }

  LowIrBridge destruct(CallOut callout) {
    def bridge = new LowIrBridge(new LowIrNode(metaText: 'begin callout params'))
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
    def bridge = new LowIrBridge(new LowIrNode(metaText: 'begin methodcall params'))
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
    def bridge = new LowIrBridge(new LowIrNode(metaText: 'begin ret'))
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

    def lowirBinop = new LowIrBinOp(leftTmpVar: leftBridge.tmpVar, tmpVar: binop.tmpVar, op: binop.op)
    
    switch(binop.op){
    case NOT:
      return leftBridge.seq(new LowIrValueBridge(lowirBinop))
    case AND:
      def tNode = new LowIrIntLiteral(value: 1, tmpVar: binop.tmpVar)
      def fNode = new LowIrIntLiteral(value: 0, tmpVar: binop.tmpVar)
      def endNode = new LowIrValueNode(metaText: '&& endnode', tmpVar: binop.tmpVar)
      LowIrNode.link(tNode, endNode)
      LowIrNode.link(fNode, endNode)
      def b2Node = shortcircuit(binop.right, tNode, fNode)
      def b1Node = shortcircuit(binop.left, b2Node, fNode)
      return new LowIrValueBridge(b1Node, endNode)  
    case OR:
      def tNode = new LowIrIntLiteral(value: 1, tmpVar: binop.tmpVar)
      def fNode = new LowIrIntLiteral(value: 0, tmpVar: binop.tmpVar)
      def endNode = new LowIrValueNode(metaText: '|| endnode', tmpVar: binop.tmpVar)
      LowIrNode.link(tNode, endNode)
      LowIrNode.link(fNode, endNode)
      def b2Node = shortcircuit(binop.right, tNode, fNode) 
      def b1Node = shortcircuit(binop.left, tNode, b2Node)
      return new LowIrValueBridge(b1Node, endNode)
    default:
      def rightBridge = destruct(binop.right)
      lowirBinop.rightTmpVar = rightBridge.tmpVar
      leftBridge = leftBridge.seq(rightBridge)
      return leftBridge.seq(new LowIrValueBridge(lowirBinop))
    }
  }

  //2 cases, local scalars (param/local) and global scalars (global/array)
  LowIrBridge destruct(Assignment assignment) {
    def srcBridge = destruct(assignment.expr)
    if (assignment.loc.descriptor.tmpVar == null) {
      def bridge = new LowIrBridge(new LowIrNode(metaText: 'global index placeholder (st)'))
      if (assignment.loc.indexExpr != null) {
        bridge = destruct(assignment.loc.indexExpr)
      }
      def storeBridge = new LowIrBridge(new LowIrStore(desc: assignment.loc.descriptor, value: srcBridge.tmpVar))
      //it is an array, so we need to link the index to it
      if (bridge instanceof LowIrValueBridge) {
        storeBridge.end.index = bridge.tmpVar
      }
      return srcBridge.seq(bridge).seq(storeBridge)
    } else {
      //local scalar
      def lowir = new LowIrMov(src: srcBridge.tmpVar, dst: assignment.loc.descriptor.tmpVar)
      return srcBridge.seq(new LowIrBridge(lowir))
    }
  }

  //2 cases, local scalars (param/local) and global scalars (global/array)
  //this is always loads; stores take place in assignments
  LowIrBridge destruct(Location loc) {
    //global variable
    if (loc.descriptor.tmpVar == null) {
      def bridge = new LowIrBridge(new LowIrNode(metaText: 'global index placeholder (ld)'))
      if (loc.indexExpr != null) {
        bridge = destruct(loc.indexExpr)
      }
      def valBridge = new LowIrValueBridge(new LowIrLoad(tmpVar: loc.tmpVar, desc: loc.descriptor))
      //it is an array, so we need to link the index to it
      if (bridge instanceof LowIrValueBridge) {
        valBridge.end.index = bridge.tmpVar
      }
      return bridge.seq(valBridge)
    } else {
      //it's a local/param
      return new LowIrValueBridge(new LowIrValueNode(metaText: 'location value',tmpVar: loc.descriptor.tmpVar))
    }
  }

  LowIrBridge destruct(IfThenElse ifthenelse) {
    def trueBridge = destruct(ifthenelse.thenBlock)
    def falseBridge = ifthenelse.elseBlock ? destruct(ifthenelse.elseBlock) : new LowIrBridge(new LowIrNode(metaText: 'empty else'))
    def endNode = new LowIrNode(metaText: 'end if')
    LowIrNode.link(falseBridge.end, endNode)
    LowIrNode.link(trueBridge.end, endNode)
    return new LowIrBridge(shortcircuit(ifthenelse.condition, trueBridge.begin, falseBridge.begin), endNode)
  }

  LowIrNode shortcircuit(condition, LowIrNode trueNode, LowIrNode falseNode) {
    def condBridge = condition instanceof LowIrValueBridge ? condition : destruct(condition)
    def jumpNode = new LowIrCondJump(trueDest: trueNode, falseDest: falseNode, condition: condBridge.tmpVar)
    condBridge = condBridge.seq(new LowIrBridge(jumpNode))
    LowIrNode.link(condBridge.end, trueNode)
    LowIrNode.link(condBridge.end, falseNode)
    return condBridge.begin
  }

  def forLoopBreakContinueStack = []

  LowIrBridge destruct(ForLoop forloop) {
    def indexTmpVar = forloop.index.descriptor.tmpVar

    def initBridge = destruct(forloop.low)
    initBridge = initBridge.seq(new LowIrBridge(new LowIrMov(src: initBridge.tmpVar, dst: indexTmpVar)))
    def finalValBridge = destruct(forloop.high)
    initBridge = initBridge.seq(finalValBridge)

    //make the inc bridge
    def oneLiteral = new LowIrIntLiteral(value: 1, tmpVar: desc.tempFactory.createLocalTemp())
    def sumBinOp = new LowIrBinOp(
      leftTmpVar: indexTmpVar,
      rightTmpVar: oneLiteral.tmpVar,
      op: BinOpType.ADD,
      tmpVar: desc.tempFactory.createLocalTemp()
    )
    def movOp = new LowIrMov(src: sumBinOp.tmpVar, dst: indexTmpVar)
    def incBridge = new LowIrBridge(oneLiteral).seq(new LowIrBridge(sumBinOp)).seq(new LowIrBridge(movOp))

    //make the cmp bridge
    def cmpBridge = new LowIrBridge(new LowIrNode(metaText: "for loop cmp (index is ${indexTmpVar})")).seq(new LowIrValueBridge(new LowIrBinOp(
      op: BinOpType.LT,
      tmpVar: desc.tempFactory.createLocalTemp(),
      leftTmpVar: indexTmpVar,
      rightTmpVar: finalValBridge.tmpVar
    )))

    def endNode = new LowIrNode(metaText: 'for loop end')

    forLoopBreakContinueStack << [endNode, incBridge.begin]
    def bodyBridge = destruct(forloop.block).seq(incBridge)
    forLoopBreakContinueStack.pop()

    def cmpNode = shortcircuit(cmpBridge, bodyBridge.begin, endNode)
    LowIrNode.link(incBridge.end, cmpNode)
    LowIrNode.link(initBridge.end, cmpNode)
    return new LowIrBridge(initBridge.begin, endNode)
  }

  LowIrBridge destruct(Continue continueHiir) {
    def continueNode = new LowIrNode(metaText: 'continue')
    LowIrNode.link(continueNode, forLoopBreakContinueStack[-1][1])
    return new LowIrBridge(continueNode, new LowIrNode(metaText: 'continue spurious node'))
  }

  LowIrBridge destruct(Break breakHiir) {
    def breakNode = new LowIrNode(metaText: 'break')
    LowIrNode.link(breakNode, forLoopBreakContinueStack[-1][0])
    return new LowIrBridge(breakNode, new LowIrNode(metaText: 'break spurious node'))
  }
}
