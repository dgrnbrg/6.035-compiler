package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class LocalCSE {
  def visitedNodes = new HashSet()
  def basicBlocks = []
  def findBasicBlocks(LowIrNode startNode) {
    def node = startNode
    if (node in visitedNodes) return
    def block = []
    //add to block until we encounter a jump src or dest or we fall off the end
    while (!(node in visitedNodes)) {
      visitedNodes << node
      block << node
      if (node.successors.size() != 1 || node.predecessors.size() > 1) break
      node = node.successors[0]
    }
    basicBlocks << block

    //follow jumps
    if (node.successors.size() > 1) {
      node.successors.each{findBasicBlocks(it)}
    }

    //new block after destination
     if (node.successors.size() == 1) {
       findBasicBlocks(node.successors[0])
     }
  }

  def contractMove(node) {
    while (node instanceof LowIrMov) node = node.src.defSite
    return node
  }

  def run(MethodDescriptor methodDesc) {
    findBasicBlocks(methodDesc.lowir)
    basicBlocks.findAll{it.size() > 1}.each { bb ->
      def vn = new ValueNumberer()
      def rewrite = [:]
      bb.each { node ->
        if (node instanceof LowIrMov || node instanceof LowIrPhi) return
        if (node instanceof LowIrStore) {
          def newRewrite = [:]
          rewrite.each { k, v -> if (k.varDesc != node.desc) newRewrite[k] = v }
          rewrite = newRewrite
        }
        if (node instanceof LowIrMethodCall) {
          def newRewrite = [:]
          rewrite.each { k, v ->
            if (!node.descriptor.getDescriptorsOfNestedStores().any{it == k.varDesc})
              newRewrite[k] = v
          }
          rewrite = newRewrite
        }
        def expr
        switch (node) {
        case LowIrBinOp:
          expr = new Expression(op: node.op, right: node.rightTmpVar, left: node.leftTmpVar)
          break
        case LowIrBoundsCheck:
          expr = new Expression(boundTest: node.testVar, boundHigh: node.upperBound, boundLow: node.lowerBound, boundDesc: node.desc)
          break
        case LowIrLoad:
          expr = new Expression(varDesc: node.desc, index: node.index)
          break
        default:
          expr = new Expression(unique: true)
          break
        }
        if (!expr.unique && expr.constVal == null && rewrite.containsKey(expr)) {
          //replace with earlier def
          if (expr.boundTest == null) {
            assert node.predecessors.size() == 1
            def mov = new LowIrMov(src: rewrite[expr].getDef(), dst: node.getDef())
            new LowIrBridge(mov).insertBefore(node)
          }
          node.excise()
          //println "OMNOMNOMNOMNOM CSE $node $node.label based on ${rewrite[expr]} ${rewrite[expr].label}"
        } else {
          //add to map
          rewrite[expr] = node
        }
      }
    }
  }
}
