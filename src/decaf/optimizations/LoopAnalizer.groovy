package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class LoopAnalizer {
  def domComps = new DominanceComputations()
  def loops = []

  def run(startNode) {
    domComps.computeDominators(startNode)
    eachNodeOf(startNode) { node ->
      if (node.metaText == 'landing pad') {
        if (node.anno['loop-contains-breaks']) return //can't do anything, it's not simple
        def exit = node.anno['loop-exit-node']
        def header = node
        while (header && !header.predecessors.any{domComps.dominates(header, it)}) {
          assert header.successors.size() == 1
          header = header.successors[0]
        }
        assert header.predecessors.size() == 2
        //now we've found the loop header, and we already know the exit node
        //so we'll try to enumerate the body
        def visited = new LinkedHashSet()
        def toVisit = new LinkedHashSet([header])
        while (toVisit.size() != 0) {
          def cur = toVisit.iterator().next()
          toVisit.remove(cur)
          visited << cur
          if (cur != exit) {
            toVisit.addAll(cur.successors - visited)
          }
        }
        //ensure that we get the nodes between the exit node and header
        //these should only form a basic block
        def missing = header.predecessors.find{domComps.dominates(header, it)}
        assert missing != null
        while (!(missing in visited)) {
          visited << missing
          assert missing.predecessors.size() == 1
          missing = missing.predecessors[0]
        }
        loops << new Loop(header: header, exit: exit, body: visited)
      }
    }
  }

  static def copyLoop(Loop loopIn, tempFactory) {
    def tmpCopyMap = new LazyMap({ TempVar it -> it == null ? null : tempFactory.createLocalTemp() })
    def nodeCopyMap 
    nodeCopyMap = new LazyMap({ LowIrNode node ->
      switch (node) {
      case LowIrIntLiteral:
        return new LowIrIntLiteral(value: node.value, tmpVar: tmpCopyMap[node.tmpVar])
      case LowIrBinOp:
        return new LowIrBinOp(
          leftTmpVar: tmpCopyMap[node.leftTmpVar],
          rightTmpVar: node.rightTmpVar ? tmpCopyMap[node.rightTmpVar] : null,
          tmpVar: tmpCopyMap[node.tmpVar],
          op: node.op
        )
      case LowIrMov:
        return new LowIrMov(src: tmpCopyMap[node.src], dst: tmpCopyMap[node.dst])
      case LowIrBoundsCheck:
        return new LowIrBoundsCheck(
          desc: node.desc,
          testVar: tmpCopyMap[node.testVar],
          lowerBound: node.lowerBound,
          upperBound: node.upperBound
        )
      case LowIrLoad:
        return new LowIrLoad(
          desc: node.desc,
          index: tmpCopyMap[node.index],
          tmpVar: tmpCopyMap[node.tmpVar]
        )
      case LowIrStore:
        return new LowIrStore(
          desc: node.desc,
          index: tmpCopyMap[node.index],
          value: tmpCopyMap[node.value]
        )
      case LowIrPhi:
        return new LowIrPhi(
          args: node.args.collect{tmpCopyMap[it]},
          tmpVar: tmpCopyMap[node.tmpVar]
        )
      case LowIrStringLiteral:
        return new LowIrStringLiteral(value: node.value, tmpVar: tmpCopyMap[node.tmpVar])
      case LowIrReturn:
      case LowIrMethodCall: assert false //shouldn't happen
      case LowIrCallOut:
        return new LowIrCallOut(
          name: node.name,
          paramTmpVars: node.paramTmpVars.collect{tmpCopyMap[it]},
          tmpVar: tmpCopyMap[node.tmpVar]
        )
      case LowIrCondJump:
        return new LowIrCondJump(
          condition: tmpCopyMap[node.condition],
          trueDest: nodeCopyMap[node.trueDest],
          falseDest: nodeCopyMap[node.falseDest]
        )
      case LowIrValueNode:
        return new LowIrValueNode(metaText: node.metaText, tmpVar: tmpCopyMap[node.tmpVar])
      case LowIrNode:
        return new LowIrNode(metaText: node.metaText)
      }
    })
    def loopOut = new Loop(header: nodeCopyMap[loopIn.header], exit: nodeCopyMap[loopIn.exit])
    loopOut.body = loopIn.body.collect{nodeCopyMap[it]}
    def bodyInSet = new HashSet(loopIn.body)
    loopIn.body.each{ node ->
      node.successors.findAll{it in bodyInSet}.each { succ ->
        LowIrNode.link(nodeCopyMap[node], nodeCopyMap[succ])
      }
    }
    return [loopOut, tmpCopyMap, nodeCopyMap]
  }
}

class Loop {
  LowIrNode header
  LowIrCondJump exit
  Collection body

  String toString() { "Loop(start: ${header?.label}, end: ${exit?.label})" }
}
