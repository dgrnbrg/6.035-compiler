package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

//This is the entry point into the analyses and rewriting
//it defines several important sets that the analyses use
class LazyCodeMotion {

//This first group is the base sets used by all the analyses

  //exprDEE(b) contains expressions defined in b that survive to the end of b
  // (downward exposed expressions)
  Set exprDEE(LowIrNode node) {
    switch (node) {
    case LowIrLoad:
    case LowIrBinOp:
    case LowIrStore:
      return new HashSet([valueNumberer.getExpr(node)])
    default:
      return new HashSet()
    }
  }

  //exprUEE (b) contains expressions defined in b that have upward exposed arguments (both args)
  //  (upward exposed expressions)
  Set exprUEE(LowIrNode node) {
    switch (node) {
    case LowIrLoad:
    case LowIrBinOp:
      return new HashSet([valueNumberer.getExpr(node)])
    //LowIrStore changes the value of a load so it's not upward exposed
    default:
      return new HashSet()
    }
  }

  //set of expressions whose arguments were killed by the node
  //TODO: I think this only matters for loads/stores since tmps are in SSA already
  Set exprKill(LowIrNode node) {
    def set = new HashSet()
    if (node.getDef() != null )
      set.addAll(exprsContainingExpr[valueNumberer.getExprOfTmp(node.getDef())])
    switch (node) {
    case LowIrMethodCall:
      set.addAll(node.descriptor.getDescriptorsOfNestedStores().collect{new Expression(varDesc: it)})
      break
    case LowIrStore:
      set << new Expression(varDesc: node.desc)
      break
    }
    return set
  }

  //this is for doing \bar{input}
  Set not(Set input) {
    input = allExprs - input
    return input
  }

//Here we create the needed analyses and define their result sets
  //computes available expressions
  def availAnal = new ClosureAnalizer(
    init: { new HashSet(allExprs) },
    xfer: {node, input ->
      def set = exprDEE(node)
      set.addAll(input.intersect(not(exprKill(node))))
      return set
    },
    joinFn: {node -> 
      def preds = node.predecessors
      if (preds) {
        return preds.inject(load(preds[0])){set, pred -> set.retainAll(load(pred)); set}
      } else {
        return new HashSet()
      }
    },
    dir: AnalysisDirection.FORWARD
  )
  //computes anticipatable expressions
  def antAnal = new ClosureAnalizer(
    init: { new HashSet(allExprs) },
    xfer: {node, input ->
      def set = exprUEE(node)
      set.addAll(input.intersect(not(exprKill(node))))
      return set
    },
    joinFn: {node -> 
      def succs = node.successors
      if (succs) {
        return succs.inject(load(succs[0])){set, succ -> set.retainAll(load(succ)); set}
      } else {
        return new HashSet()
      }
    },
    dir: AnalysisDirection.BACKWARD
  )
  //push expressions down the flowgraph
  def laterAnal = new ClosureAnalizer(
    init: { new HashSet() },
    worklistInit: { startNode ->
      def wl = new LinkedHashSet()
      eachNodeOf(startNode) { node ->
        wl.addAll(node.predecessors.collect{pred -> new LCMEdge(node,pred)})
      }
      return wl
    },
    xfer: {edge, input ->
      def i = edge.fst
      def j = edge.snd
      assert i != j
      return earliest(i,j) + (input.intersect(not(exprUEE(i))))
    },
    joinFn: {edge -> 
      def preds = edge.fst.predecessors
      if (preds) {
        return preds.inject(load(new LCMEdge(preds[0], edge.fst))){set, pred ->
          set.retainAll(load(new LCMEdge(pred, edge.fst)))
          return set
        }
      } else {
        return new HashSet()
      }
    },
    dir: AnalysisDirection.FORWARD
  )

  def availIn(LowIrNode node) { return availAnal.join(node) }
  def availOut(LowIrNode node) { return availAnal.transfer(node, availAnal.join(node)) }

  def antIn(LowIrNode node) { return antAnal.transfer(node, antAnal.join(node)) }
  def antOut(LowIrNode node) { return antAnal.join(node) }

  def laterIn(j) {laterAnal.join(new LCMEdge(j, null))}
  def later(i,j) {laterAnal.transfer(new LCMEdge(i,j), laterIn(i))}

  def insertAlongEdge(i,j) {later(i,j).intersect(not(laterIn(j)))}
  def deleteNode(k) { k != startNode ? exprUEE(k).intersect(not(laterIn(k))) : new HashSet()}

  // e \in earliest(i,j) if 
  //  - it can move to head of j
  //  - it is not available at end of i
  //  - either to cannot move to head of i or another edge leaving i prevents its placement in i
  Set earliest(LowIrNode i, LowIrNode j) {
     if (i == startNode)
       return antIn(j).intersect(not(availOut(startNode)))
     else
       return antIn(j).intersect(not(availOut(i))).intersect(exprKill(i) + not(antOut(i)))
  }

//this part prepares the graph-dependent sets
  def allExprs = new LinkedHashSet()
  def exprsContainingExpr = new LazyMap({new LinkedHashSet()})
  def startNode
  def initialize(LowIrNode startNode) {
    this.startNode = startNode
    eachNodeOf(startNode) {
      def expr
      expr = valueNumberer.getExpr(it)
      it.anno['expr'] = expr //TODO: delete this annotation
      switch (it) {
      case LowIrLoad:
      case LowIrBinOp:
      case LowIrStore:
      case LowIrIntLiteral:
        if (it.getDef() != null) {
          it.getUses().each{use ->
            exprsContainingExpr[valueNumberer.getExprOfTmp(use)] << expr
          }
        }
        allExprs << expr
        break
      }
      if (it.successors.size() == 0) antAnal.store(it, new HashSet()) //establish starting values
    }
    availAnal.store(startNode, new HashSet()) //establish starting values
  }

//Here's the entry point
  def valueNumberer = new ValueNumberer() //this is used to decide equivalencies of expressions

  def run(MethodDescriptor desc) {
    initialize(desc.lowir)
    availAnal.analize(startNode)
    antAnal.analize(startNode)
    laterAnal.analize(startNode)

    eachNodeOf(desc.lowir) {
      it.anno['antIn'] = antIn(it)
      it.anno['antOut'] = antOut(it)
      it.anno['availIn'] = availIn(it)
      it.anno['availOut'] = availOut(it)
      it.anno['DEExpr'] = exprDEE(it)
      it.anno['UEExpr'] = exprUEE(it)
      it.anno['exprKill'] = exprKill(it)
      if (it.predecessors.size() == 1) {
        it.anno['earliest'] = earliest(it.predecessors[0], it)
      }
      it.anno['laterIn'] = laterIn(it)
      it.anno['later'] = it.successors.inject(new HashSet()){set, succ -> set + later(it, succ)}
      it.anno['insert'] = it.predecessors.inject(new HashSet()){set, pred -> set + insertAlongEdge(pred, it)}
      it.anno['delete'] = deleteNode(it)
    }
    //first, insert instructions whereever they need to be placed
    //Then, for each definition, search the dominator tree for the redundant definition
    //  and replace the current computation with a mov

    //To do this, we compute the dominator tree and do an in-order walk of it
    //we maintain a map of what the most recent definition of some expression is in
    //when we encounter a node that has an edge which needs some expression inserted along it
    //  we store the edge and the constructed expression into a deferred insertion list
    //finally, we insert the expressions
    newTmp = {-> desc.tempFactory.createLocalTemp()}
    domComps = new DominanceComputations()
    domComps.computeDominators(startNode)
    findAndReplace(desc.lowir)
    uniqueToTmp = valueNumberer.uniqueToTmp
    for (action in toInsertOrDelete) {
      domComps = new DominanceComputations()
      domComps.computeDominators(startNode)
//      valueNumberer = new ValueNumberer()
//println "####################### $action"
//      doRewrite(action)
    }
  }

  def newTmp
  def domComps
  def toInsertOrDelete = new LinkedHashSet()
  def uniqueToTmp
  def findDefInDomTree(LowIrNode node, expr) {
    if (uniqueToTmp.containsKey(expr)) return uniqueToTmp[expr]
    while (node != null && valueNumberer.getExpr(node) != expr) {
      if (expr.op != null) {
        //println "$node doesn't contain $expr (it's ${valueNumberer.getExpr(node)})"
      }
      node = domComps.ancestor[node]
    }
    if (node instanceof LowIrStore) return node.value
    //TODO: this shouldn't happen after we fix the initial definitions
    if (node == null) {
      def t = newTmp()
      //println "@@@returning new $t for $expr"
      return t
    }
    assert node.getDef() != null
    return node.getDef()
  }
  def findAndReplace(LowIrNode node) {
    for (expr in deleteNode(node)) {
      toInsertOrDelete << [expr, node]
    }
    for (succ in node.successors) {
      for (expr in insertAlongEdge(node, succ)) {
        toInsertOrDelete << [expr, node, succ]
      }
    }
    domComps.domTree[node].each{findAndReplace(it)}
  }

  def doRewrite(List action) {
    if (action.size() == 3) {
      def fst = action[1], snd = action[2]
      if (action[2].predecessors.size() > 1) {
        new LowIrBridge(new LowIrNode(metaText: 'lcm rewrite')).insertBefore(action[2])
      }
      assert action[2].predecessors.size() == 1
      fst = snd.predecessors[0]
//      assert insertAlongEdge(fst, snd).size() > 0
      def expr = action[0]
        expr.check()
        def node
        if (expr.constVal != null) {
          node = new LowIrIntLiteral(value: expr.constVal, tmpVar: newTmp())
        } else if (expr.unique) {
          assert false //I think this can't happen
        } else if (expr.op != null) {
          node = new LowIrBinOp(
            op: expr.op,
            leftTmpVar: findDefInDomTree(fst, expr.left),
            rightTmpVar: expr.right ? findDefInDomTree(fst, expr.right) : null,
            tmpVar: newTmp()
          )
//if (expr.op == BinOpType.LT) println "inserting an LT"
        } else if (expr.varDesc != null) {
          //TODO: this smells like arrays will be totally wrong
          node = new LowIrLoad(desc: expr.varDesc, tmpVar: newTmp())
        } else {
          assert false
        }
        
        node.anno['expr'] = expr
        node.tmpVar.defSite = node
        valueNumberer.memo[node] = expr
        new LowIrBridge(node).insertBetween(fst, snd)
    } else {
      def del = action[1]
      def expr = action[0]
//if (expr.op == BinOpType.LT) println "deleting an LT"
      assert del.predecessors.size() == 1
      def tmp = findDefInDomTree(del.predecessors[0], expr)
//      println "replacing ${del.getDef()} with $tmp"
      if (tmp.defSite != null) {
        del.getDef().useSites.clone().each{
          assert it.replaceUse(del.getDef(), tmp) > 0
        }
        del.excise() //delete
      }
    }
  }
}

class MutatedGraphException extends Exception {
  MutatedGraphException(String msg) {
    super(msg);
  }
}

//This is how we do dataflow analysis for the laterAnal, since it's on edges
//LCM = LazyCodeMotion
class LCMEdge implements GraphNode {
  LowIrNode fst, snd

  List getPredecessors() {
    assert false
  }

  List getSuccessors() {
    return snd.successors.collect{new LCMEdge(snd, it)}
  }

  LCMEdge(fst, snd) {
    assert fst != snd
    this.fst = fst
    this.snd = snd
  }

  int hashCode() {
    return (fst?.hashCode() ?: 0) * 17 + (snd?.hashCode() ?: 0) * 19
  }

  boolean equals(Object o) {o instanceof LCMEdge && o.hashCode() == this.hashCode()}

  String toString() { "E($fst, $snd)" }
}

//This class lets you define an analizer by providing closures rather than subclassing Analizer
class ClosureAnalizer extends Analizer {
  Closure xfer // transfer function
  Closure init // default value of map function
  Closure joinFn //join function
  @Lazy Map map = new LazyMap(init)

  void setJoinFn(Closure joinFn) {
    this.@joinFn = joinFn
    this.joinFn.delegate = this
  }

  void setXfer(Closure xfer) {
    this.@xfer = xfer
    this.xfer.delegate = this
  }

  Set transfer(GraphNode node, Set input) { xfer(node, input) }
  Set join(GraphNode node) { joinFn(node) }

  void store(GraphNode key, Set data) { map[key] = data }
  Set load(GraphNode key) { map[key] }
}
