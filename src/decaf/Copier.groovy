package decaf

class Copier {
  def tempFactory
  def tmpCopyMap = new LazyMap({ TempVar it -> it == null ? null : tempFactory.createLocalTemp() })
  def nodeCopyMap

  Copier(tempFactory) {
    this.tempFactory = tempFactory
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
println "copying phi"
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
  }

  def copy(Collection nodes) {
    def out = nodes.collect{nodeCopyMap[it]}
    def inSet = new HashSet(nodes)
    out.each{ node ->
      node.successors.findAll{it in inSet}.each { succ ->
        LowIrNode.link(nodeCopyMap[node], nodeCopyMap[succ])
      }
    }
    return out
  }
}
