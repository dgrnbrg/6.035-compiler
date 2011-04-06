package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class PartialRedundancyElimination {
  def allExprs = new LinkedHashSet()
  def exprsContainingExpr = new LazyMap({new LinkedHashSet()})
  def startNode

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

  Set not(Set input) {
    input = allExprs - input
    return input
  }

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


  def availIn(LowIrNode node) {
    return availAnal.join(node)
  }

  def availOut(LowIrNode node) {
    return availAnal.transfer(node, availAnal.join(node))
  }

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
        return succs.inject(load(succs[0])){set, succ -> set.addAll(load(succ)); set}
      } else {
        return new HashSet()
      }
    },
    dir: AnalysisDirection.BACKWARD
  )

  def antIn(LowIrNode node) {
    //For some reason, the below statement doesn't work, so we recompute it instead
//    return antAnal.load(node)
    return antAnal.transfer(node, antAnal.join(node))
  }

  def antOut(LowIrNode node) {
    return antAnal.join(node)
  }

  // e \in earliest(i,j) if 
  //  - it can move to head of j
  //  - it is not available at end of i
  //  - either to cannot move to head of i or another edge leaving i prevents its placement in i
//TODO: the final not() might be misplaced--currently, it's different than the slides indicate
  Set earliest(LowIrNode i, LowIrNode j) {
//     if (antIn(j).intersect(not(availOut(i))).intersect(not(exprKill(i) + antOut(i))).size() == 0)
//       println "${antIn(j)} intersect ${not(availOut(i))} intersect ${exprKill(i)} + ${not(antOut(i))}"

//     if (antIn(j).intersect(not(availOut(i))).intersect(exprKill(i) + not(antOut(i))).size() == 0)
//println "fuck"

if (i.label == 'label10') {
}
     if (i == startNode)
       return antIn(j).intersect(not(availOut(startNode)))
     else
       return antIn(j).intersect(not(availOut(i))).intersect(exprKill(i) + not(antOut(i)))
  }

/*
  //edges represented as 2 elt lists
  def laterAnal = [
    later: {LowIrNode i, LowIrNode j ->
      return earliest(i,j) + (laterAnal.laterIn(i).intersect(not(exprUEE(i))))
    },
    laterIn: {node -> 
      def preds = node.predecessors
      if (preds) {
        return preds.inject(load([node,preds[0]])){set, pred ->
          set.retainAll(laterAnal.map[([node, pred])])
          return set
        }
      } else {
        return new HashSet()
      }
    }
    laterMap: [:]
//    dir: AnalysisDirection.FORWARD
  ]

  def laterAnalize(GraphNode start) {
    startNode.successors.each { laterAnal.map[([startNode, it])] = new HashSet() }
    def worklist = new LinkedHashSet(startNode.successors.collect{[startNode, it]})
    
    while (worklist.size() != 0) {
      def edge = worklist.iterator().next()
      worklist.remove(edge)
      def old = laterAnal.map[edge].clone()
      def input = laterAnal.laterIn(edge)
      def out = transfer(node, input)
      if (old != out) {
        worklist.addAll(node.getSuccessors())
      }
      store(node, out)
    }
  }
*/

  def laterAnal = new ClosureAnalizer(
    init: { new HashSet() },
    worklistInit: { startNode ->
      def wl = new LinkedHashSet()
      eachNodeOf(startNode) { node ->
//println "${node.predecessors.size()} $node"
        wl.addAll(node.predecessors.collect{pred -> new LCMEdge(node,pred)})
      }
//println "initialized laterAnal to size ${wl.size()}"
wl.each{if (it.fst == it.snd) println "FFFFFFFFFFFFFFFFFFUUUUUUUUUUUUUUUUUU"}
      return wl
    },
    xfer: {edge, input ->
      def i = edge.fst
      def j = edge.snd
assert i != j
      def ret = earliest(i,j) + (input.intersect(not(exprUEE(i))))
if (j.label == 'label10') {
  println "later(x, label10) = $ret, earliest($i,$j) = ${earliest(i,j)}"
}
      return ret
    },
    joinFn: {edge -> 
      def preds = edge.fst.predecessors
      if (preds) {
        def blah = preds.inject(load(new LCMEdge(preds[0], edge.fst))){set, pred ->
          set.retainAll(load(new LCMEdge(pred, edge.fst)))
          return set
        }
blah.each{if (it.fst == it.snd) println "FFFFFFFFFFFFFFFFFFUUUUUUUUUUUUUUUUUU"}
        return blah
      } else {
        return new HashSet()
      }
    },
    dir: AnalysisDirection.FORWARD
  )

  def laterIn(j) {laterAnal.join(new LCMEdge(j, null))}
  def later(i,j) {laterAnal.transfer(new LCMEdge(i,j), laterIn(i))}

  def insertAlongEdge(i,j) {later(i,j).intersect(not(laterIn(j)))}
  def deleteNode(k) { k != startNode ? exprUEE(k).intersect(not(laterIn(k))) : new HashSet()}

  def valueNumberer = new ValueNumberer()

  def run(MethodDescriptor desc) {
    startNode = desc.lowir
    eachNodeOf(desc.lowir) {
      def expr
      expr = valueNumberer.getExpr(it)
      it.anno['expr'] = expr
      switch (it) {
      case LowIrLoad:
      case LowIrBinOp:
      case LowIrStore:
      case LowIrIntLiteral:
/*        it.getUses().each { use ->
          exprsContainingTmp[use] << expr
        }
*/
        if (it.getDef() != null) {
          it.getUses().each{use ->
            exprsContainingExpr[valueNumberer.getExprOfTmp(use)] << expr
          }
        }
        allExprs << expr
        break
      }
      if (it.successors.size() == 0) antAnal.store(it, new HashSet())
    }
println allExprs
    availAnal.store(desc.lowir, new HashSet())
    availAnal.analize(desc.lowir)
//println "#########################"
    antAnal.analize(desc.lowir)
//println "#########################"
    laterAnal.analize(desc.lowir)
/*
    //insert optimal placements
    eachNodeOf(desc.lowir) {
      for (pred in it.predecessors) {
        def expressions = insertAlongEdge(pred, it)
        //TODO: insert these expressions
      }
    }
    //remove redundant nodes
    eachNodeOf(desc.lowir) {
      if (deleteNode(it).size() != 0) {
        //TODO: port createRedundancy and make this phase work
        it.excise()
      }
    }
*/
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
if (it.label == 'label10') {
        println "down here: earliest(${it.predecessors[0]} $it) = ${it.anno['earliest']}"
}
      }
      it.anno['laterIn'] = laterIn(it)
      it.anno['later'] = it.successors.inject(new HashSet()){set, succ -> set + later(it, succ)}
      it.anno['insert'] = it.predecessors.inject(new HashSet()){set, pred -> set + insertAlongEdge(pred, it)}
      it.anno['delete'] = deleteNode(it)
//it.successors.inject(globalAntAnal.load(it.successors[0])){set, succ ->
//        set.retainAll(globalAntAnal.load(succ)); set}
    }
println " ------- end function ${desc.name} ------- "
  }
}

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

  void store(key, Set data) { 
//if (key instanceof LowIrNode && key.label == 'label10') println "storing into label 10 $data"
map[key] = data 
}
  Set load(key) {
//if (key instanceof LowIrNode && key.label == 'label10') println "loading from label 10 ${map[key]}"
 map[key] }
}

class LCMEdge implements GraphNode {
  LowIrNode fst, snd

  List getPredecessors() {
    assert false
  }

  List getSuccessors() {
    def succs = snd.successors.collect{new LCMEdge(snd, it)}
succs.each{if (it.fst == it.snd) println "FFFFFFFFFFFFFFFFFFUUUUUUUUUUUUUUUUUU"}
    return succs
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

class Expression {
  //const, binop, unique, and variable
  def constVal
  def unique = false
  def left, right, op
  def varDesc

  def check() {
    if (constVal != null) {
      assert !unique && left == null && right == null && op == null && varDesc == null
    }
    if (unique) {
      assert constVal == null && left == null && right == null && op == null && varDesc == null
    }
    if (left) {
      assert constVal == null && !unique && varDesc == null
    }
    if (varDesc) {
      assert !unique && constVal == null && left == null && right == null && op == null
    }
  }

  int hashCode() {
    check()
    if (unique) return super.hashCode() //hash of pointer
    if (constVal != null) return 103*constVal
    if (op != null) {
      return left.hashCode() * 17 + op.hashCode() * 41 + right.hashCode() * 91
    }
    if (varDesc != null) {
      return varDesc.hashCode()
    }
  }

  boolean equals(Object o) { o instanceof Expression && o.hashCode() == this.hashCode() }

  String toString() {
    check()
    if (unique) return "unique${this.hashCode() % 100}"
    if (constVal != null) return "const($constVal)"
    if (op != null) return "($left $op $right)"
    if (varDesc != null) return "desc($varDesc.name)"
    assert false
  }
}

class ValueNumberer {
  def equivalences = [:] //equiv[dst] = src for mov
  def constants = [:] //const[tmpVar] = K for IntLiteral
  def numberCache = [:] //cache[tmpVar] = n to get a value number, but it might be empty

  def visitedPhis //TODO: don't get stuck in phi loops
  def getExpr(LowIrNode node) {
    visitedPhis = new HashSet()
    return getExprOfNode(node)
  }

  //maintains uniqueness for values
  def uniqueMap = new LazyMap({new Expression(unique: true)})

  def getExprOfTmp(TempVar tmp) {
    //if param, unique
    //if undef, 0
    //if known, node
    if (tmp.type == TempVarType.PARAM) {
      return uniqueMap[tmp]
    } else if (tmp.defSite == null) {
      return new Expression(constVal: 0)
    } else {
      return getExprOfNode(tmp.defSite)
    }
  }

  def memo = [:] // (memoize)
  def getExprOfNode(LowIrNode node) {
    if (memo.containsKey(node)) return memo[node]

    def result
    switch (node) {
    case LowIrMov:
      result = getExprOfTmp(node.src)
      break
    case LowIrCallOut:
    case LowIrMethodCall:
      result = uniqueMap[node]
      break
    case LowIrIntLiteral:
      result = new Expression(constVal: node.value)
      break
    case LowIrBinOp:
      def lhs = getExprOfTmp(node.leftTmpVar)
      def rhs = getExprOfTmp(node.rightTmpVar)
      result = new Expression(left: lhs, right: rhs, op: node.op)
      break
    case LowIrStore:
    case LowIrLoad:
      result = new Expression(varDesc: node.desc)
      break
    case LowIrPhi:
      if (visitedPhis.contains(node)) {
        result = uniqueMap[node]
        break
      }
      visitedPhis << node
      def exprs = node.args.collect{getExprOfTmp(it)}.findAll{it != uniqueMap[node]}
      if (exprs.every{it == exprs[0]}) {
        result = exprs[0]
        break
      } else {
        result = uniqueMap[node]
        break
      }
    case LowIrCondJump:
    case LowIrReturn:
    case LowIrStringLiteral:
    default:
      //no expr/unique expr are the same
      result = uniqueMap[node]
      break
    }

    memo[node] = result
    return result
  }

/*
  def getValueNumber(TempVar tmpVar) {
    //follow through moves
    while (equivalences.containsKey(tmpVar)) tmpVar = equivalences[tmpVar]
    //could be const, param, default (0), phi, call, binop
    if (constants.containsKey(tmpVar)) {
    } else if (tmpVar.type == TempVarType.PARAM) {
    } else if (tmpVar.defSite == null) {
    } else {
      switch (tmpVar.defSite) {
      case 
      }
    }
  }
*/
  void run(MethodDescriptor desc) {
  }
}

