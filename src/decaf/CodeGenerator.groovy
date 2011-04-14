package decaf
import decaf.graph.*
import static decaf.BinOpType.*

class CodeGenerator extends Traverser {
  def asm = [text: ['.text'], strings: ['.data'], bss: ['.bss']]
  def paramRegs = []
  MethodDescriptor method

  CodeGenerator() {
    paramRegs = [rdi, rsi, rdx, rcx, r8, r9]
  }

  void handleMethod(MethodDescriptor method) {
    this.method = method
    asmMacro('.globl', method.name)
    emit(method.name + ':')
    enter(8*(method.params.size() + method.tempFactory.tmpVarId),0)
    // Part of pre-trace codegen
    // traverse(method.lowir)
    traverseWithTraces(method.lowir)
  }

  Operand getTmp(TempVar tmp){
    switch (tmp.type) {
    case TempVarType.PARAM:
      return rbp(8 * (tmp.id+2))
    case TempVarType.LOCAL: 
      return rbp(-8 * (tmp.id+1))
    default:
      assert false
    }
  }

  void visitNode(GraphNode stmt) {
    // Part of pre-trace codegen.
    /*if (!stmt.frak) {
      stmt.frak = true
    } else {
      return
    }*/

    def predecessors = stmt.getPredecessors()
    def successors = stmt.getSuccessors()

    //assert no X nodes, only ^ (branch) or V nodes (join) 
    if (predecessors.size() > 1) {
      assert successors.size() <= 1
    }
    if (successors.size() > 1) {
      assert predecessors.size() <= 1
    }

    // Part of pre-trace codegen.
    //emit(stmt.label + ':')

    // Print label if:
    if(stmt.anno["trace"]["start"] || stmt.anno["trace"]["JmpDest"]) {
      emit(stmt.label + ':')
    }

    switch (stmt) {
    case LowIrStringLiteral:
      def strLitOperand = asmString(stmt.value)
      strLitOperand.type = OperType.IMM
      movq(strLitOperand, getTmp(stmt.tmpVar))
      break
    case LowIrIntLiteral:
      movq(new Operand(stmt.value), getTmp(stmt.tmpVar))
      break
    case LowIrCallOut:
      def paramsOnStack = stmt.paramTmpVars.size() - paramRegs.size()
      if (paramsOnStack > 0){
        sub(8*paramsOnStack, rsp)
      }

      stmt.paramTmpVars.eachWithIndex {tmpVar, index ->
        if (index < paramRegs.size()) {
          movq(getTmp(tmpVar), paramRegs[index])
        } else {
          movq(getTmp(tmpVar),r10)
          movq(r10,rsp(8*(index - paramRegs.size())))
        }
      }
      if (stmt.name == 'printf') {
        movq(0,rax)
      }
      call(stmt.name)
      movq(rax,getTmp(stmt.tmpVar))
      if (paramsOnStack > 0){
	add(8*paramsOnStack, rsp)
      }
      break
    case LowIrMethodCall:
      sub(8*stmt.paramTmpVars.size(), rsp)
      stmt.paramTmpVars.eachWithIndex { it, index ->
        movq(getTmp(it), r10)
        movq(r10, rsp(8*index))
      }
      call(stmt.descriptor.name)
      movq(rax,getTmp(stmt.tmpVar))
      add(8*stmt.paramTmpVars.size(), rsp)
      break
    case LowIrBoundsCheck:
      movq(getTmp(stmt.testVar), r10)
      doBoundsCheck(stmt.lowerBound, stmt.upperBound, r10)
      break
    case LowIrReturn:
      if (stmt.tmpVar != null) {
        movq(getTmp(stmt.tmpVar),rax)
      } else {
        movq(0,rax) //void fxns return 0
      }
      leave()
      ret()
      break
    case LowIrCondJump:
      movq(getTmp(stmt.condition), r11)
      cmp(1, r11)
      je(stmt.trueDest.label)
      // Part of pre-tracer codegen.      
      //jmp(stmt.falseDest.label)
      break
    case LowIrLoad:
      if (stmt.index != null) {
        movq(getTmp(stmt.index), r11)
        def arrOp = r11(stmt.desc.name + '_globalvar', 8)
        movq(arrOp, r10)
      } else {
        movq(new Operand(stmt.desc.name + '_globalvar'), r10)
      }
      movq(r10, getTmp(stmt.tmpVar))
      break
    case LowIrStore:
      movq(getTmp(stmt.value), r10)
      if (stmt.index != null) {
        movq(getTmp(stmt.index), r11)
        def arrOp = r11(stmt.desc.name + '_globalvar', 8)
        movq(r10, arrOp)
      } else {
        movq(r10, new Operand(stmt.desc.name + '_globalvar'))
      }
      break
    case LowIrMov:
      movq(getTmp(stmt.src), r10)
      movq(r10, getTmp(stmt.dst))
      break
    case LowIrBinOp:
      switch (stmt.op) {
      case GT:
	movq(getTmp(stmt.leftTmpVar), r10)
	movq(getTmp(stmt.rightTmpVar), r11)
	cmp(r11, r10)
	movq(1, r10)
	movq(0, r11)
	cmovg(r10, r11)
	movq(r11, getTmp(stmt.tmpVar))
	break
      case LT:
	movq(getTmp(stmt.leftTmpVar), r10)
	movq(getTmp(stmt.rightTmpVar), r11)
	cmp(r11, r10)
	movq(1, r10)
	movq(0, r11)
	cmovl(r10, r11)
	movq(r11, getTmp(stmt.tmpVar))
	break
      case LTE:
	movq(getTmp(stmt.leftTmpVar), r10)
	movq(getTmp(stmt.rightTmpVar), r11)
	cmp(r11, r10)
	movq(1, r10)
	movq(0, r11)
	cmovle(r10, r11)
	movq(r11, getTmp(stmt.tmpVar))
	break
      case GTE:
	movq(getTmp(stmt.leftTmpVar), r10)
	movq(getTmp(stmt.rightTmpVar), r11)
	cmp(r11, r10)
	movq(1, r10)
	movq(0, r11)
	cmovge(r10, r11)
	movq(r11, getTmp(stmt.tmpVar))
	break
      case EQ:
	movq(getTmp(stmt.leftTmpVar), r10)
	movq(getTmp(stmt.rightTmpVar), r11)
	cmp(r11, r10)
	movq(1, r10)
	movq(0, r11)
	cmove(r10, r11)
	movq(r11, getTmp(stmt.tmpVar))
	break
      case NEQ:
	movq(getTmp(stmt.leftTmpVar), r10)
	movq(getTmp(stmt.rightTmpVar), r11)
	cmp(r11, r10)
	movq(1, r10)
	movq(0, r11)
	cmovne(r10, r11)
	movq(r11, getTmp(stmt.tmpVar))
	break
      case NOT:
        movq(getTmp(stmt.leftTmpVar), r10)
	xor(1, r10)
	movq(r10, getTmp(stmt.tmpVar))
	break
      case ADD:
        movq(getTmp(stmt.leftTmpVar),r10)
        movq(getTmp(stmt.rightTmpVar),r11)
        add(r10,r11)
        movq(r11,getTmp(stmt.tmpVar))
        break
      case SUB:
        movq(getTmp(stmt.leftTmpVar),r10)
        movq(getTmp(stmt.rightTmpVar),r11)
        sub(r11,r10)
        movq(r10,getTmp(stmt.tmpVar))
        break
      case MUL:
        movq(getTmp(stmt.leftTmpVar),r10)
        movq(getTmp(stmt.rightTmpVar),r11)
        imul(r10,r11)
        movq(r11,getTmp(stmt.tmpVar))
        break
      case DIV:
        movq(getTmp(stmt.leftTmpVar),rax)
        movq(0,rdx)
        movq(getTmp(stmt.rightTmpVar),r10)
        idiv(r10)
        movq(rax,getTmp(stmt.tmpVar))
        break
      case MOD:
        movq(getTmp(stmt.leftTmpVar),rax)
        movq(0,rdx)
        movq(getTmp(stmt.rightTmpVar),r10)
        idiv(r10)
        movq(rdx,getTmp(stmt.tmpVar))
        break
      default:
        throw new RuntimeException("still haven't implemented that yet: $stmt $stmt.op")
      }
      break
    case LowIrPhi:
      break
    case LowIrNode: //this is a noop
      assert stmt.getClass() == LowIrNode.class || stmt.getClass() == LowIrValueNode.class
      break
    default:
      assert false
    }

    // Part of pre-trace CodeGen.
    /*if (successors.size() == 1) {
      jmp(successors[0].label)
    } else if (successors.size() == 0) {
      jmp(method.name + '_end')
    }*/

    if(stmt.anno["trace"]["FalseJmpSrc"]) {
      jmp(stmt.falseDest.label)
    } else if(stmt.anno["trace"]["JmpSrc"]) {
      jmp(stmt.anno["trace"]["JmpSrc"])
    }
  }

  def asmString(String s) {
    def label = genPrivateLabel('strings')
    asmMacro('strings', '.string', "\"$s\"")
    return label
  }

  def asmMacro(String region = 'text', String macro, arg) {
    String result = macro + ' ' + arg
    emit(region, result)
  }

  int privateLabelIndex = 0
  def genPrivateLabel(region = 'text') {
    def label = ".label${privateLabelIndex++}".toString()
    emit(region, label + ':')
    return new Operand(label)
  }

  def emit(region = 'text', s) {
    asm[region] << s
  }

  void link(GraphNode src, GraphNode dst) {
  }

  static int boundsLabelCounter = 0
  def genBoundsLabel() {
    return "bounds_check_${boundsLabelCounter++}".toString()
  }

  //the access is in the inRegister, low and high are ints that will checked
  def doBoundsCheck(int low, int high, inRegister) {
    def boundsLabel = genBoundsLabel()
    def boundsLabelPost = genBoundsLabel()

    cmp(high, inRegister)
    jge(boundsLabel)

    cmp(low, inRegister)
    jl(boundsLabel)

    jmp(boundsLabelPost)

    emit(boundsLabel + ':')
    dieWithMessage("Array out of bounds\\n");

    emit(boundsLabelPost + ':')
  }



  def getAsm() {
    StringBuilder code = new StringBuilder()
    asm.values().each{ list ->
     list.each {
      code.append(it.toString() + '\n')
     }
    }
    return code.toString()
  }

  def dieWithMessage(String msg) {
    def strLitOperand = asmString('RUNTIME ERROR: '+msg)
    strLitOperand.type = OperType.IMM
    movq(strLitOperand, rdi)
    movq(0, rax)
    call('printf')
    movq(1,rdi)
    call('exit')
  }

  static Map nameToInstrType = {
    def tmp = [:]
    EnumSet.allOf(InstrType.class).each{ tmp[it.name] = it }
    tmp
  }()

  static Map nameToReg = {
    def tmp = [:]
    EnumSet.allOf(Reg.class).each{ tmp[it.name] = it }
    tmp
  }()

  def methodMissing(String name, args) {
    if (nameToReg.containsKey(name)) {
      assert args.size() == 1 || args.size() == 2
      def op = new Operand(args[0], nameToReg[name])
      if (args.size() == 2) {
        op.stride = args[1]
      }
      return op
    } else if (nameToInstrType.containsKey(name)) {
      def type = nameToInstrType[name]
      assert args.size() == type.numOperands
      def instr = new Instruction(type)
      //convert arguments to immediates if needed
      args = args.collect { !(it instanceof Operand) ? new Operand(it) : it }
      if (args.size() >= 1) {
        instr.op1 = args[0]
      }
      if (args.size() >= 1) {
        instr.op2 = args[1]
      }
      emit(instr)
      return
    }
    throw new MissingMethodException(name,getClass(),args)
  }

  def propertyMissing(String name) {
    if (nameToReg.containsKey(name)) {
      return new Operand(nameToReg[name])
    }
    throw new MissingPropertyException(name, getClass())
  }
}
