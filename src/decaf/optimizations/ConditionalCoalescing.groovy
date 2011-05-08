package decaf.optimizations
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.BinOpType.*
import static decaf.graph.Traverser.eachNodeOf

class ConditionalCoalescing {

  def analize(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    def toCoalesce = new LazyMap() //key: comparison binop node, value: condjump nodes
    def cmpable = [GT, LT, EQ, NEQ, GTE, LTE]
    eachNodeOf(startNode) { node ->
      def predNode = node.predecessors[0]
      if (node instanceof LowIrBinOp) {
        def useNodes = node.getDef().useSites 
        def op = node.op
        if (op in cmpable && useNodes.any{it instanceof LowIrCondJump}) {
           toCoalesce[node] = useNodes.findAll{it instanceof LowIrCondJump}
        }
      }
    }

    toCoalesce.each { node, jmps ->
      def useNodes = node.getDef().useSites
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
      if (jmps.size() == node.getDef().useSites.size())
        node.excise()
    }
  }
}
