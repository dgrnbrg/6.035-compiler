package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class ConditionalCoalescing {

  def analize(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    def toCoalesce = new LazyMap() //key: comparison binop node, value: condjump nodes
    eachNodeOf(startNode) { node ->
      def predNode = node.predecessors[0]
      if (node instanceof LowIrBinOp) {
        def useNodes = node.getDef().useSites 
        def op = node.op
        def cmp = op == BinOpType.GT || op == BinOpType.LT || op == BinOpType.EQ ||
                op == BinOpType.LTE || op == BinOpType.GTE || op == BinOpType.NEQ
        if (cmp && useNodes.any{it instanceof LowIrCondJump}) {
           toCoalesce[node] = useNodes.findAll{it instanceof LowIrCondJump}
        }
      }
    }
//println toCoalesce

    toCoalesce.keySet().each { node ->
//      assert node.predecessors.size() == 1
      def jmps = toCoalesce[node]
      jmps.each { jmp ->
        assert jmp.predecessors.size() == 1
        def pred = jmp.predecessors[0]
        new LowIrBridge(new LowIrCondCoalesced(condition: jmp.condition, trueDest: jmp.trueDest, falseDest: jmp.falseDest,
          op: node.op, leftTmpVar: node.leftTmpVar, rightTmpVar: node.rightTmpVar, tmpVar: node.tmpVar)).insertBetween(pred, jmp)
        def coalescedPred = jmp.predecessors[0]
        jmp.successors.each{ coalescedPred.successors << it }
        LowIrNode.unlink(coalescedPred, jmp)
        coalescedPred.successors.remove(jmp)
      }
      node.excise()
    }
  }
}
