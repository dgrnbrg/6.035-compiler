package decaf
import decaf.*
import decaf.graph.GraphNode
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class SparseConditionalConstantPropagation {

  //setlattice value to UNDEF for all nodes
  def tmpToInstVal = new LazyMap({ //keys:tmpVars, value: InstValues
    if (it && it.type == TempVarType.PARAM)
      return new InstVal(LatticeType.OVERDEF)
    else
      return new InstVal(LatticeType.UNDEF)
  })
  def flowWL = new LinkedHashSet()
  //the ssaWL is initially empty
  def ssaWL = new LinkedHashSet()
  def edges = new LazyMap( { node -> node.predecessors.collect { [it, node]} })
  def execFlag = new LazyMap( { false })
  def toUnlink = []

  def analize(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    store(startNode, new InstVal())
    //init flowWL to contain edges exiting the start node of the program
    startNode.successors.each { flowWL << [startNode, it]}
    //set executable flag for all edges (in our case, nodes)
    while (!flowWL.isEmpty() || !ssaWL.isEmpty()) {
      //halt when both worklists become empty
      if (!flowWL.isEmpty()) {
        def e = flowWL.iterator().next()
        def a = e[0]
        def b = e[1]
        flowWL.remove(e)
        //propagate constants along flowgraph edges

        if (!execFlag[[a,b]]) {
          execFlag[[a,b]] = true
          if (b instanceof LowIrPhi) {
            
            visitPhi(b)
            store(b, tmpToInstVal[b.getDef()])
          } else if (edgeCount(b, edges) == 1) {
            visitInst(b)
            store(b, tmpToInstVal[b.getDef()])
          }
        }
      }
      if (!ssaWL.isEmpty()) {
        def e = ssaWL.iterator().next()
        def a = e[0]
        def b = e[1]
        ssaWL.remove(e)
        def EC = edgeCount(b, edges)
        if (b instanceof LowIrPhi) {
          visitPhi(b)
          store(b, tmpToInstVal[b.getDef()])
        } else if (edgeCount(b, edges) >=1 ) {
          visitInst(b)
          store(b, tmpToInstVal[b.getDef()])
        }
      }
    }
  }

  void store(GraphNode node, InstVal data) {
    //assert data != null
    node.anno['instVal'] = data
  }

  int edgeCount(LowIrNode node, LazyMap edges) {
    //return number of executable edges leading to b
    def nodeEdges = edges[node]
    int i = 0
    nodeEdges.each { if (execFlag.getAt(it) == true) {i++} }
    return i
  }

  //symbolic execution to simulate arithmetic
  InstVal sccpEval(LowIrNode node) {
    //def valueChanged = false
    def result = null
    switch (node) {
    case LowIrBinOp:
      switch (node.op) {
      case BinOpType.NOT: 
        result = calculate(node, {x -> 
          new InstVal(x == 0 ? 1 : 0) })
        break
      case BinOpType.ADD:
        result = calculate(node, {x, y -> new InstVal(x + y) })
        break
      case BinOpType.SUB: 
        result = calculate(node, {x, y -> new InstVal(x - y) })
        break
      case BinOpType.MUL:
        //check whether either of the operands is a const and is zero -- if so, the expression is zero
        def leftType = tmpToInstVal[node.leftTmpVar].latticeVal
        def rightType = tmpToInstVal[node.rightTmpVar].latticeVal
        if ((leftType == LatticeType.CONST && tmpToInstVal[node.leftTmpVar].constVal == 0)
             || rightType == LatticeType.CONST && tmpToInstVal[node.rightTmpVar].constVal == 0) {
          result = new InstVal(0)
        } else {
          result = calculate(node, {x, y -> new InstVal(x * y) })
        }
        break
      case BinOpType.DIV:
        //check whether the dividend is a const and zero -- if so, the expression is zero
        def leftType = tmpToInstVal[node.leftTmpVar].latticeVal
        def rightType = tmpToInstVal[node.rightTmpVar].latticeVal
        if (leftType == LatticeType.CONST && tmpToInstVal[node.leftTmpVar].constVal == 0
           && !(rightType == LatticeType.CONST && tmpToInstVal[node.rightTmpVar].constVal == 0)) {
          result = new InstVal(0)
        } else {
          try {
            result = calculate(node, {x, y -> new InstVal(x / y) })
          } catch (ArithmeticException e) {
            throw new FatalException(msg: "During symbolic execution of constants in the program, we determined that division by zero is attempted. Please, change the program to avoid this.", code: 1)
          }
        }
        break
      case BinOpType.MOD:
        //check whether the dividend is a const and zero -- if so, the expression is zero
        def leftType = tmpToInstVal[node.leftTmpVar].latticeVal
        def rightType = tmpToInstVal[node.rightTmpVar].latticeVal
        if (leftType == LatticeType.CONST && tmpToInstVal[node.leftTmpVar].constVal == 0
           && ! (rightType == LatticeType.CONST && tmpToInstVal[node.rightTmpVar].constVal == 0)) {
          result = new InstVal(0)
        } else {
          try {
            result = calculate(node, {x, y -> new InstVal(x % y) })
          } catch (ArithmeticException e) {
            throw new FatalException(msg: "During symbolic execution of constants in the program, we determined that division by zero is attempted. Please, change the program to avoid this.", code: 1)
          }
        }
        break
      case BinOpType.LT:
        result = calculate(node, {x,y -> new InstVal( x < y ? 1 : 0) })
        break
      case BinOpType.GT:
        result = calculate(node, {x,y -> new InstVal( x > y ? 1 : 0) })
        break
      case BinOpType.LTE:
        result = calculate(node, {x,y -> new InstVal( x <= y ? 1 : 0) })
        break
      case BinOpType.GTE:
        result = calculate(node, {x,y -> new InstVal( x >= y ? 1 : 0) })
        break
      case BinOpType.EQ:
        result = calculate(node, {x,y -> new InstVal( x == y ? 1 : 0) })
        break
      case BinOpType.NEQ:
        result = calculate(node, {x,y -> new InstVal( x != y ? 1 : 0) })
        break
      case BinOpType.AND:
        result = calculate(node, {x,y -> new InstVal( x & y ) })
        break
      case BinOpType.OR:
        result = calculate(node, {x,y -> new InstVal( x | y ) })
        break
      }
      break
    case LowIrPhi:
      assert false
    case LowIrMov:
    case LowIrLoad:
    case LowIrCallOut:
    case LowIrMethodCall:
    case LowIrIntLiteral:
    default:
      result = calculate(node, null)
      break
    }
    return result
  }

  def visitPhi(LowIrPhi node) {
    def result
    if (node.getUses().any { tmpToInstVal[it].latticeVal == LatticeType.OVERDEF}) {
      result =  new InstVal(LatticeType.OVERDEF)
    } else if (node.getUses().every { tmpToInstVal[it].latticeVal == LatticeType.UNDEF}) {
      result = new InstVal(LatticeType.UNDEF)
    } else {
      def listConsts = node.getUses().collect{ tmpToInstVal[it] }.findAll { it.latticeVal == LatticeType.CONST }*.constVal
      assert listConsts.size() > 0
      result = listConsts.every { it == listConsts[0] } ? new InstVal(listConsts[0]) : new InstVal(LatticeType.OVERDEF)
    }
    if (result != tmpToInstVal[node.getDef()]) {
        tmpToInstVal[node.getDef()] = result
        node.getDef().useSites.each { ssaWL << [node, it] }
    }
    tmpToInstVal[node.getDef()] = result
    if (node.successors.size() == 1) {
      flowWL << [node, node.successors[0]]
    } else {
      assert node.successors.size() == 0
    }
    return result
  }

  def visitInst(LowIrNode node) {
    def result = sccpEval(node)
    if (node.getDef() != null && result != tmpToInstVal[node.getDef()]) {
        tmpToInstVal[node.getDef()] = result
        node.getDef().useSites.each { ssaWL << [node, it] }
    }
    switch (node) {
    case LowIrCondJump:
      def value = tmpToInstVal[node.condition]
      if (value.latticeVal == LatticeType.OVERDEF) {
        node.successors.each { flowWL << [ node, it ] }
      }
      else if (value.latticeVal == LatticeType.CONST) {
        assert node.successors.size() == 2
        def val = value.constVal
        assert val == 0 || val == 1
        if (val == 1) {
          flowWL << [node, node.trueDest]
        } else {
          flowWL << [node, node.falseDest]
        }
      }
      break
    default:
      if (node.successors.size() == 1) {
        flowWL << [node, node.successors[0]]
      } else {
        assert node.successors.size() == 0
      }
      break
    }
  }

  InstVal calculate(LowIrNode node, Closure resolve) {
    switch (node) {
    case LowIrBinOp:
      if (node.getUses().any { tmpToInstVal[it].latticeVal == LatticeType.UNDEF }) {
        return new InstVal(LatticeType.UNDEF)
      } else if (node.getUses().any {tmpToInstVal[it].latticeVal == LatticeType.OVERDEF }) {
        return new InstVal(LatticeType.OVERDEF)
      } else {
        //all uses are constants
        def args = node.getUses().collect { tmpToInstVal[it].constVal }
        def instVal = resolve(*args)
        return instVal
      }
    case LowIrMov:
      return tmpToInstVal[node.src]
    case LowIrLoad:
      return new InstVal(LatticeType.OVERDEF)
    case LowIrCallOut:
      return new InstVal(LatticeType.OVERDEF)
    case LowIrMethodCall:
      return new InstVal(LatticeType.OVERDEF)
    case LowIrIntLiteral:
      return new InstVal(node.value)
    default:
      return new InstVal(LatticeType.OVERDEF)
    }
  }

  def run(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    analize(methodDesc)
    eachNodeOf(startNode) { node ->
      def latType = tmpToInstVal[node.getDef()].latticeVal
      if (latType == LatticeType.CONST && node.getDef() != null) {
        def constVal = tmpToInstVal[node.getDef()].constVal
        assert node.successors.size() == 1
        new LowIrBridge(new LowIrIntLiteral(value: constVal, tmpVar: node.getDef())).
          insertBetween(node, node.successors[0])
        node.excise()
      }
      if (node instanceof LowIrCondJump &&
          tmpToInstVal[node.condition].latticeVal == LatticeType.CONST) {
        toUnlink << node
      }
      if (node instanceof LowIrBinOp) {
        def leftVar = tmpToInstVal[node.leftTmpVar]
        def rightVar = tmpToInstVal[node.rightTmpVar]
        def isConstOne = { it.latticeVal == LatticeType.CONST && it.constVal == 1}
        def isOverDef = {it.latticeVal == LatticeType.OVERDEF}
        if (node.op == BinOpType.MUL && isConstOne(leftVar) && isOverDef(rightVar)) {
          assert node.successors.size() == 1
          new LowIrBridge(new LowIrMov(src: node.rightTmpVar, dst:node.tmpVar)).insertBetween(node, node.successors[0])
          node.excise()
        } else if (node.op == BinOpType.MUL && isConstOne(rightVar) && isOverDef(leftVar)) {
          assert node.successors.size() == 1
          new LowIrBridge(new LowIrMov(src: node.leftTmpVar, dst:node.tmpVar)).insertBetween(node, node.successors[0])
          node.excise()
        }
        //TODO: +- 0
      }
    }
    toUnlink.each {
      def br = null
      assert it.predecessors.size() == 1
      def pred = it.predecessors[0]
      def val = tmpToInstVal[it.condition].constVal
      if (val == 1) {
        br = it.trueDest
      } else if (val == 0) {
        br = it.falseDest
      } else {
        assert false
      }
      LowIrNode.unlink(pred, it)
      LowIrNode.unlink(it, br)
      LowIrNode.link(pred, br)
    }
  }

}

class InstVal {
  LatticeType latticeVal
  int constVal

  InstVal() { }

  InstVal(LatticeType latticeVal) {
    this.latticeVal = latticeVal
  }

  InstVal(Number constVal) {
    this.latticeVal = LatticeType.CONST
    this.constVal = constVal as int
  }

  int hashCode() { latticeVal.hashCode()*31 + constVal }

  boolean equals(Object other) {
    other instanceof InstVal && other.hashCode() == this.hashCode()
  }

  String toString() { "InstVal($latticeVal, $constVal)" }
}

enum LatticeType {
  UNDEF,     //instruction has no known value
  CONST,      //instruction has constant value
  OVERDEF    //instruction may have multiple value
}
