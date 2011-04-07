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
    def domComps = new DominanceComputations()
    domComps.computeDominators(startNode)
    //initially, any expr has no defs
    def recentDefs = new LazyMap({ expr ->
      if (expr.unique) return [valueNumberer.uniqueToTmp[expr]]
      else return []
    })
    def deferredInserts = [] //list of lists=[before, after, bridge]
    def newTmp = {-> desc.tempFactory.createLocalTemp() }
    def findAndDefer //predefine closure name or else it can't be self-recursive
    findAndDefer = { node ->
      //first, we defer any insertions and store their recent defs
      for (succ in node.successors) {
        for (expr in insertAlongEdge(node, succ)) {
println "found an insertable expr"
          expr.check()
          def bridge
          if (expr.constVal != null) {
            bridge = new LowIrBridge(new LowIrIntLiteral(value: expr.constVal, tmpVar: newTmp()))
          } else if (expr.unique) {
            assert false //I think this can't happen
          } else if (expr.op != null) {
            def leftNode, rightNode
            if (expr.left.constVal != null && recentDefs[expr.left].size() == 0) {
              leftNode = new LowIrIntLiteral(value: 0, tmpVar: newTmp())
              recentDefs[expr.left] << leftNode.tmpVar
            }
            if (expr.right.constVal != null && recentDefs[expr.right].size() == 0) {
              rightNode = new LowIrIntLiteral(value: 0, tmpVar: newTmp())
              recentDefs[expr.right] << rightNode.tmpVar
            }
println "$expr.left ${recentDefs[expr.left]}"
//TODO: note the the current error appears to be due to mistraversal or something
            def binopNode = new LowIrBinOp(
              op: expr.op,
              leftTmpVar: recentDefs[expr.left][-1],
              rightTmpVar: recentDefs[expr.right][-1]
            )
            def nodeList = []
            if (leftNode != null) {
              recentDefs[expr.left].pop()
              nodeList << leftNode
            }
            if (rightNode != null) {
              recentDefs[expr.right].pop()
              nodeList << rightNode
            }
            nodeList << binopNode
            bridge = new LowIrBridge(nodeList)
          } else if (expr.varDesc != null) {
            //TODO: this smells like arrays will be totally wrong
            bridge = new LowIrBridge(new LowIrLoad(desc: expr.desc, tmpVar: newTmp()))
          } else {
            assert false
          }
          deferredInserts << [node, succ, bridge]
        }
      }
      //next, we push a def if it exists
      def definedExpr = valueNumberer.getExpr(node)
      if (node.getDef()) {
        recentDefs[definedExpr] << node.getDef()
      } else if (node instanceof LowIrStore) {
        recentDefs[definedExpr] << node.value
      } else {
        definedExpr = null
      }
      //then, we recur
      domComps.domTree[node].each{ findAndDefer(it) }
      //finally, we pop the def
      if (definedExpr != null) {
        recentDefs[definedExpr].pop()
      }
    }
    findAndDefer(desc.lowir)

    //now, we can insert the replacements
    deferredInserts.each {
      it[2].insertBetween(it[0], it[1])
    }

    //replacement to come...
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
