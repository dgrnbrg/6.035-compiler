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
        def newCond = new LowIrCondCoalesced(trueDest: jmp.trueDest,
          falseDest: jmp.falseDest,
          op: node.op, leftTmpVar: node.leftTmpVar, rightTmpVar: node.rightTmpVar)
        assert jmp.predecessors.size() == 1
        LowIrNode.link(jmp.predecessors[0], newCond)
        LowIrNode.unlink(jmp.predecessors[0], jmp)
        jmp.successors.clone().each {
          LowIrNode.link(newCond, it)
          LowIrNode.unlink(jmp, it)
        }
        jmp.condition.useSites.remove(jmp)
      }
      if (jmps.size() == node.getDef().useSites.size())
        node.excise()
    }
  }
}
