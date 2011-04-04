package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf
import static decaf.BinOpType.*

class CommonSubexpressionElimination extends Analizer{

  def allExprs = new LinkedHashSet()
  def exprsContainingTmp = new LazyMap({new LinkedHashSet()})

  def methodToClobbers = new LazyMap({
    def clobbers = new LinkedHashSet()
    eachNodeOf(it.lowir){node -> if (node instanceof LowIrStore) clobbers << node.desc }
    return clobbers
  })

  def tempFactory
  def run(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    tempFactory = methodDesc.tempFactory
    store(startNode, new LinkedHashSet())
    eachNodeOf(startNode) {
      def expr
      expr = new AvailableExpr(it)
      switch (it) {
      case LowIrLoad:
      case LowIrBinOp:
        it.getUses().each { use ->
          exprsContainingTmp[use] << expr
        }
        allExprs << expr
        break
      }
    }
    analize(startNode)
    eachNodeOf(startNode) {
      def expr
      switch (it) {
      case LowIrLoad:
      case LowIrBinOp:
        expr = new AvailableExpr(it)
        break
      }
      if (expr != null) {
        if (it.predecessors.every{pred -> load(pred).contains(expr)}) {
/*
          def tmpVar = methodDesc.tempFactory.createLocalTemp()
          def worklist = new LinkedHashSet(it.predecessors)
          def visited = new HashSet(worklist)
          while (worklist.size() > 0) {
            def candidate = worklist.iterator().next()
            worklist.remove(candidate)
            visited << candidate
            //we're BFS backwards from the node to find the defsites
            if ((candidate instanceof LowIrBinOp || candidate instanceof LowIrLoad)
                 && new AvailableExpr(candidate) == expr) {
              //now, we insert a move to the temporary
              //since candidate isn't a condjump, it has one successor
              def mov = new LowIrMov(src: candidate.getDef(), dst: tmpVar)
              assert candidate.successors.size() == 1
              new LowIrBridge(mov).insertBetween(candidate, candidate.successors[0])
            } else {
              worklist += candidate.predecessors - visited
            }
          }
*/
          rootNode = it
          tmpVarCache = [:]
          def tmpVar = createRedundancy(it, expr)
          //now, insert mov so that from tmpvar to redundant expr's destination
          assert it.successors.size() == 1
          new LowIrBridge(new LowIrMov(src: tmpVar, dst: expr.tmpVar)).insertBetween(
            it, it.successors[0])
          it.excise()
        }
      }
    }
  }

  def getExpressionOf(node) {
    if (node instanceof LowIrBinOp || node instanceof LowIrLoad) {
      return new AvailableExpr(node)
    } else if (node instanceof LowIrStore) {
      return new AvailableExpr(new LowIrLoad(tmpVar: node.value, desc: node.desc))
    } else {
      return [tmpVar: null]
    }
  }

  def rootNode
  Map tmpVarCache
  TempVar createRedundancy(LowIrNode startNode, targetExpression) {
    def nodeUnderInspection = startNode
    def exprUnderInspection = getExpressionOf(nodeUnderInspection)
    def walkedNodes = [nodeUnderInspection]
    while (nodeUnderInspection.predecessors.size() == 1
        && !tmpVarCache.containsKey(nodeUnderInspection)) {
      if (exprUnderInspection == targetExpression && nodeUnderInspection != rootNode) {
        break
      }
      nodeUnderInspection = nodeUnderInspection.predecessors[0]
      exprUnderInspection = getExpressionOf(nodeUnderInspection)
      walkedNodes << nodeUnderInspection
    }

    if (tmpVarCache.containsKey(nodeUnderInspection)) return tmpVarCache[nodeUnderInspection]

    if (exprUnderInspection == targetExpression) {
      walkedNodes.each { tmpVarCache[it] = exprUnderInspection.tmpVar }
      return exprUnderInspection.tmpVar
    } else if (nodeUnderInspection.predecessors.size() > 1) {
      //we need to insert a phi before this node, but first, find the 2 tmpVars
      //if all are equal, return it; otherwise, insert phi function
      def tmps = nodeUnderInspection.predecessors.collect{ createRedundancy(it, targetExpression) }
      if (tmps.findAll{it == tmps[0]}.size() == tmps.size()) {
        walkedNodes.each { tmpVarCache[it] = tmps[0] }
        return tmps[0]
      } else {
        def phi = new LowIrPhi(tmpVar: tempFactory.createLocalTemp(), args: tmps)
        new LowIrBridge(phi).insertBefore(nodeUnderInspection)
        walkedNodes.each { tmpVarCache[it] = phi.tmpVar }
        return phi.tmpVar
      }
    } else {
      assert false, "shouldn't reach a start node"
    }
  }

  // map from nodes to (map from availableExpr to first def site)
  def availExprMap = new LazyMap({[:]})

  void store(GraphNode node, Set data) {
    def newMap = [:]
    data.each { newMap[it] = it.node.getDef() }
    availExprMap[node] = newMap
    node.anno['avail']=data
  }

  Set load(GraphNode node) {
    return new LinkedHashSet(availExprMap[node].keySet())
  }

  Set transfer(GraphNode node, Set input) {
    return (input - kill(node)) + gen(node)
  }

  Set join(GraphNode node) {
    if (node.predecessors) {
      return node.predecessors.inject(load(node.predecessors[0]).clone()) { set, succ -> set.retainAll(load(succ)); set }
    } else
      return new LinkedHashSet()
  }

  def gen(node) {
    def set
    switch (node) {
    case LowIrBinOp:
    case LowIrLoad:
      set = Collections.singleton(new AvailableExpr(node))
      set -= kill(node)
      break
    case LowIrStore:
      set = Collections.singleton(new AvailableExpr(new LowIrLoad(desc: node.desc, tmpVar: node.value)))
      break
    default:
      set = Collections.emptySet()
      break
    }
    return set
  }

  def kill(node) {
    def set = node.getDef() != null ? exprsContainingTmp[node.getDef()] : Collections.emptySet()
    if (node instanceof LowIrMethodCall) {
      set = methodToClobbers[node.descriptor].collect{new AvailableExpr(new LowIrLoad(desc: it))}
    } else if (node instanceof LowIrStore) {
      set = Collections.singleton(new AvailableExpr(new LowIrLoad(desc: node.desc)))
    }
    return set
  }
}

class AvailableExpr {
  def node

  AvailableExpr(node) {
    this.node = node
  }

  def getTmpVar() { node.getDef() }

  int hashCode() {
    int i = 0
    assert node instanceof LowIrBinOp || node instanceof LowIrLoad
    switch (node) {
    case LowIrBinOp:
      i = node.op.hashCode() * 43 + node.leftTmpVar.hashCode() * 97
      if (node.rightTmpVar) {
        if (node.op in [ADD, MUL, EQ, NEQ, AND, OR]) i += node.rightTmpVar.hashCode() * 97
        else i += node.rightTmpVar.hashCode() * 103
      }
      break
    case LowIrLoad:
      i = node.desc.hashCode() * 4257
      if (node.index != null) i += node.index.hashCode() * 101
      break
    }
    return i
  }

  boolean equals(Object other) {
    return other != null && other.getClass() == AvailableExpr.class && other.hashCode() == this.hashCode()
  }

  String toString() {
    return "Available($node)"
  }
}
