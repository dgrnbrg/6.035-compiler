package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.BinOpType.*

class UnaryCoalesce {

  def coalesceMove(node) {
    while (node instanceof LowIrMov) node = node.src.defSite
    return node
  }

  def run(methodDesc) {
	println "unops being used"
    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      if (node instanceof LowIrBinOp && (node.op in [ADD, SUB, MUL, DIV, MOD])) {
        def right = coalesceMove(node.rightTmpVar.defSite)
        def left = coalesceMove(node.leftTmpVar.defSite)
        def newOp
        if (right instanceof LowIrIntLiteral) {
          newOp = new LowIrRightCurriedOp(
            input: node.leftTmpVar,
            constant: right.value,
            op: node.op,
            tmpVar: node.tmpVar
          )
        } else if (left instanceof LowIrIntLiteral) {
          newOp = new LowIrLeftCurriedOp(
            input: node.rightTmpVar,
            constant: left.value,
            op: node.op,
            tmpVar: node.tmpVar
          )
        }
        if (newOp) {
          assert node.successors.size() == 1
          new LowIrBridge(newOp).insertBetween(node, node.successors[0])
          node.excise()
        }
      }
    }
  }
}
