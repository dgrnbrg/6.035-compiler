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
    //Each node exposes its expressions
    // (nodes that don't generate exprs are given uniques that are never used)
    return new HashSet([valueNumberer.getExpr(node)])
  }

  //exprUEE (b) contains expressions defined in b that have upward exposed arguments (both args)
  //  (upward exposed expressions)
  Set exprUEE(LowIrNode node) {
    switch (node) {
//Note: the following relocation will break the system, so IntLiteral's aren't UEE
//    case LowIrIntLiteral:
    case LowIrLoad:
    case LowIrBinOp:
      return new HashSet([valueNumberer.getExpr(node)])
    //LowIrStore changes the value of a load so it's not upward exposed
    default:
      return new HashSet()
    }
  }

  //set of expressions whose arguments were killed by the node
  Set exprKill(LowIrNode node) {
    def set = new HashSet()
    //every expr that uses this one
    set.addAll(exprsContainingExpr[valueNumberer.getExpr(node)])
    switch (node) {
    case LowIrMethodCall:
      //Method kills anything it might store to; we don't bother to only kill certain indices
      node.descriptor.getDescriptorsOfNestedStores().each{set.addAll(exprsContainingDesc[it])}
      break
    case LowIrStore:
      //TODO: might be too conservative
      set += exprsContainingDesc[node.desc]
      break
    }
    return set
  }

  //this is for doing \bar{input}
  //it's been removed for performance reasons, but left here for easy reference
/*
  Set not(Set input) {
    input = allExprs - input
    return input
  }
*/

//Here we create the needed analyses and define their result sets
//********************
//** IMPORTANT NOTE **
//********************
//You'll see commented out lines written in a function style and then
//the imperative version is what's left in for performance reasons.

  //computes available expressions
  def availAnal = new ClosureAnalizer(
    init: { new HashSet(allExprs) },
    xfer: {node, input ->
      def set = exprDEE(node)
//      set.addAll(input.intersect(not(exprKill(node))))
      def set_prime = input.clone()
      set_prime.removeAll(exprKill(node))
      set.addAll(set_prime)
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
    //We initialize the worklist in reverse to allow the propagator to visit the nodes
    //in closer to linear time than n^2/2 time
    worklistInit: {startNode ->
      def worklist = []
      eachNodeOf(startNode) { worklist << it }
      def worklist_final = new LinkedHashSet()
      for (int i = worklist.size() - 1; i >= 0; i--) {
        worklist_final << worklist[i]
      }
      return worklist_final
    },
    xfer: {node, input ->
      def set = exprUEE(node)
      def set_prime = input.clone()
      set_prime.removeAll(exprKill(node))
      set.addAll(set_prime)
//      set.addAll(input.intersect(not(exprKill(node))))
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
//      return earliest(i,j) + (input.intersect(not(exprUEE(i))))
      def s = input
      s.removeAll(exprUEE(i))
      s.addAll(earliest(i,j))
      return s
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

//  def insertAlongEdge(i,j) {later(i,j).intersect(not(laterIn(j)))}
  def insertAlongEdge(i,j) {later(i,j) - laterIn(j)}
//  def deleteNode(k) { k != startNode ? exprUEE(k).intersect(not(laterIn(k))) : new HashSet()}
  def deleteNode(k) { k != startNode ? exprUEE(k) - laterIn(k) : new HashSet()}

  // e \in earliest(i,j) if 
  //  - it can move to head of j
  //  - it is not available at end of i
  //  - either to cannot move to head of i or another edge leaving i prevents its placement in i
  Set earliest(LowIrNode i, LowIrNode j) {
     if (i == startNode) {
       return antIn(j) - availOut(startNode)
//       return antIn(j).intersect(not(availOut(startNode)))
     } else {
//       return antIn(j).intersect(not(availOut(i))).intersect(exprKill(i) + not(antOut(i)))
       //this is faster, but differs from the above in how it looks (i.e. worse)
       def x = antIn(j)
       x.removeAll(availOut(i))
       def y = x.clone()
       y.retainAll(exprKill(i))
       x.removeAll(antOut(i))
       y.addAll(x)
       return y
     }
  }

//this part prepares the graph-dependent sets
  def allExprs = new LinkedHashSet()
  def exprsContainingExpr = new LazyMap({new LinkedHashSet()})
  def exprsContainingDesc = new LazyMap({new LinkedHashSet()})
  def startNode
  def initialize(LowIrNode startNode) {
    this.startNode = startNode
    eachNodeOf(startNode) {
      def expr
      expr = valueNumberer.getExpr(it)
      it.anno['expr'] = expr //TODO: delete this annotation
      switch (it) {
      case LowIrLoad:
      case LowIrStore:
        exprsContainingDesc[it.desc] << expr
        break
      }
      it.getUses().each{use ->
        exprsContainingExpr[valueNumberer.getExprOfTmp(use)] << expr
      }
      allExprs << expr

      if (it.successors.size() == 0) antAnal.store(it, new HashSet()) //establish starting values
    }
    availAnal.store(startNode, new HashSet()) //establish starting values
  }

//Here's the entry point
  def valueNumberer = new ValueNumberer() //this is used to decide equivalencies of expressions

  def run(MethodDescriptor desc) {
    SSAComputer.updateDUChains(desc.lowir)
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
    uniqueToTmp.each{k, v-> if (k.unique) exprToNewTmp[k] = v}
//println "initialized rewrites"
    // we need to copy these expressions
    for (action in toInsertOrDelete) {
//      valueNumberer = new ValueNumberer()
//println "####################### $action"
      doRewrite(action)
    }
//println "did insert and deletes"
    //make sure that non-deleted expressions are available too
    def needsCopy = []
    def newTmps = new HashSet(exprToNewTmp.values())
    eachNodeOf(startNode) { node ->
      if (node.getDef() in newTmps || (node instanceof LowIrMov && node.src in newTmps)) return
      if (deleteNode(node).size() != 0) return
      if (valueNumberer.getExpr(node) in exprToNewTmp.keySet().findAll{it != null && !it.unique}) {
        needsCopy << node
      }
    }
    for (node in needsCopy) {
      assert node.successors.size() == 1
      def nodeTmp
      if (node instanceof LowIrStore) nodeTmp = node.value
      else nodeTmp = node.getDef()
      def expr = valueNumberer.getExpr(node)
      new LowIrBridge(new LowIrMov(src: nodeTmp, dst: exprToNewTmp[expr])).insertBetween(
        node, node.successors[0]
      )
    }
eachNodeOf(startNode){it.anno['expr'] = valueNumberer.getExpr(it)}
//println '########'
//exprToNewTmp.each{k,v->println "$k $v"}
//println "inserted copies"
//    println "Deleted ${toInsertOrDelete.findAll{it.size() == 2}.size()}, inserted ${toInsertOrDelete.findAll{it.size() == 3}.size()}"
//    SSAComputer.destroyAllMyBeautifulHardWork(desc.lowir)
    new SSAComputer().compute(desc)
//println "SSA fixed"
  }

  def newTmp
  def domComps
  def toInsertOrDelete = new LinkedHashSet()
  def uniqueToTmp
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

  def exprToNewTmp = new LazyMap({newTmp()})
  def doRewrite(List action) {
    if (action.size() == 3) {
      def fst = action[1], snd = action[2]
      if (snd.predecessors.size() > 1) {
        new LowIrBridge(new LowIrNode(metaText: 'lcm rewrite')).insertBefore(snd)
      }
//println snd.predecessors
      assert snd.predecessors.size() == 1
      fst = snd.predecessors[0]
//      assert insertAlongEdge(fst, snd).size() > 0
      def expr = action[0]
        expr.check()
        def node
        if (expr.constVal != null) {
          node = new LowIrIntLiteral(value: expr.constVal, tmpVar: exprToNewTmp[expr])
        } else if (expr.unique) {
          assert false //I think this can't happen
        } else if (expr.op != null) {
if (expr.left.constVal == 0) {
  println "$expr ${exprToNewTmp[expr.left]} ${exprToNewTmp[expr.right]}"
}
          node = new LowIrBinOp(
            op: expr.op,
            leftTmpVar: exprToNewTmp[expr.left],
            rightTmpVar: expr.right ? exprToNewTmp[expr.right] : null,
            tmpVar: exprToNewTmp[expr]
          )
//if (node.label == 'label143') println 'hit fucked up shit'
//if (expr.op == BinOpType.LT) println "inserting an LT"
        } else if (expr.varDesc != null) {
          //TODO: this smells like arrays will be totally wrong
          node = new LowIrLoad(desc: expr.varDesc, tmpVar: exprToNewTmp[expr])
          if (expr.index != null) node.index = exprToNewTmp[expr.index]
        } else {
          assert false
        }
        
        node.anno['expr'] = expr
        node.tmpVar.defSite = node
//if (expr.constVal == 0) println "@@ $node"
        valueNumberer.memo[node] = expr
        new LowIrBridge(node).insertBetween(fst, snd)
    } else {
      def del = action[1]
      def expr = action[0]
//if (expr.op == BinOpType.LT) println "deleting an LT"
      assert del.successors.size() == 1
      def tmp = exprToNewTmp[expr]
//      println "replacing ${del.getDef()} with $tmp"
/*
      if (tmp.defSite != null) {
        del.getDef().useSites.clone().each{
          def replacements = it.replaceUse(del.getDef(), tmp)
          if (replacements == 0) println "Warning: found broken useSite"
        }
      }
*/
//      if (expr.varDesc != null) return
//if (expr.constVal == 0) println "^^ $del"
      new LowIrBridge(new LowIrMov(src: tmp, dst: del.getDef())).insertBetween(del, del.successors[0])
      valueNumberer.memo[del.successors[0]] = expr
      del.excise() //delete
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
