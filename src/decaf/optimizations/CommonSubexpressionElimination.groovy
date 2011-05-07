package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf
import static decaf.BinOpType.*

class CommonSubexpressionElimination extends Analizer{

  def allExprs = new LinkedHashSet()
  def exprsContainingExpr = new LazyMap({new LinkedHashSet()})
  def exprsContainingDesc = new LazyMap({new LinkedHashSet()})
  def valNum = new ValueNumberer()

  def tempFactory
  def run(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    tempFactory = methodDesc.tempFactory
    store(startNode, new LinkedHashSet())
    eachNodeOf(startNode) {
      def expr
      expr = valNum.getExpr(it)
      switch (it) {
      case LowIrLoad:
      case LowIrStore:
        exprsContainingDesc[it.desc] << expr
        break
      }
      it.getUses().each { use ->
        exprsContainingExpr[valNum.getExprOfTmp(use)] << expr
      }
      allExprs << expr
    }
    analize(startNode)
    def redundantExprs = new LinkedHashSet()
    def nodesToRemove = []
    eachNodeOf(startNode) {
      def expr
      switch (it) {
      case LowIrLoad:
      case LowIrBinOp:
      case LowIrBoundsCheck:
      case LowIrIntLiteral:
        expr = valNum.getExpr(it)
        break
      }
      if (expr != null) {
        if (it.predecessors.every{pred -> load(pred).contains(expr)}) {
          if (it.getDef() != null) redundantExprs << expr
          nodesToRemove << it
        }
      }
    }

    def redundanciesToCreate = []
    eachNodeOf(startNode) {
      if (valNum.getExpr(it) in redundantExprs) {
        redundanciesToCreate << it
      }
    }

    def exprToNewTmp = new LazyMap({methodDesc.tempFactory.createLocalTemp()})
    redundanciesToCreate.each{
      assert it.successors.size() == 1
      def nodeSrc = it.getDef()
      if (it instanceof LowIrStore) nodeSrc = it.value
      if (nodeSrc) {
        new LowIrBridge(new LowIrMov(src: nodeSrc, dst: exprToNewTmp[valNum.getExpr(it)])).
          insertBetween(it, it.successors[0])
      }
    }

    nodesToRemove.each{
      assert it.successors.size() == 1
      if (it.getDef() != null) {
        new LowIrBridge(new LowIrMov(src: exprToNewTmp[valNum.getExpr(it)], dst: it.getDef())).
          insertBetween(it, it.successors[0])
      }
      it.excise()
    }

    def ssaComp =new SSAComputer()
    ssaComp.destroyAllMyBeautifulHardWork(startNode)
    ssaComp.tempFactory = methodDesc.tempFactory
    ssaComp.doDominanceComputations(startNode)
    ssaComp.placePhiFunctions(startNode)
    ssaComp.rename(startNode)
  }

  def dataMap = new LazyMap({ new HashSet(allExprs) })

  void store(GraphNode node, Set data) {
    dataMap[node] = data
  }

  Set load(GraphNode node) {
    return dataMap[node]
  }

  //set of expressions whose arguments were killed by the node
  Set exprKill(LowIrNode node) {
    def set = new HashSet()
    //every expr that uses this one
    set.addAll(exprsContainingExpr[valNum.getExpr(node)])
    switch (node) {
    case LowIrMethodCall:
      //Method kills anything it might store to; we don't bother to only kill certain indices
      node.descriptor.getDescriptorsOfNestedStores().each{set.addAll(exprsContainingDesc[it])}
      break
    case LowIrBoundsCheck:
    case LowIrStore:
      //might be too conservative, in that we mess up the following:
      //a[0] = 1;
      //a[1] = 2; #here we kill a[0] = 1
      //foo(a[0]); #we generate an extra load for a[0] here maybe?
      set += exprsContainingDesc[node.desc]
      break
    }
    return set
  }

  Set transfer(GraphNode node, Set input) {
    def set = new LinkedHashSet([valNum.getExpr(node)])
    def set_prime = input.clone()
    set_prime.removeAll(exprKill(node))
    set.addAll(set_prime)
    return set
  }

  Set join(GraphNode node) {
    def preds = node.predecessors
    if (preds) {
      return preds.inject(load(preds[0]).clone()) { set, pred -> set.retainAll(load(pred)); set }
    } else
      return new LinkedHashSet()
  }
}
