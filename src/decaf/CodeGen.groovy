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

  void handleMethod(MethodDescriptor method, LowIrNode start) {
    this.method = method
    asmMacro('.globl', method.name)
    emit(method.name + ':')
    enter(8*(method.params.size() + method.maxTmpVars),0)
    traverse(start)
    if (method.returnType == Type.VOID) {
      leave()
      ret()
    } else {
      def strLitOperand = asmString("Control fell off end of non-void function $method.name\\n")
      strLitOperand.type = OperType.IMM
      movq(strLitOperand, rdi)
      movq(0, rax)
      call('printf')
      movq(1,rdi)
      call('exit')
    }
  }

  Operand getTmp(TempVar tmp){
    switch (tmp.type) {
    case TempVarType.PARAM:
      return rbp(8 * (tmp.getId()+2))
    case TempVarType.LOCAL: 
      return rbp(-8 * (tmp.getId()+1))
    case TempVarType.GLOBAL:
      return new Operand(tmp.globalName)
    default:
      assert false
    }
  }

  void visitNode(GraphNode stmt) {

    // Code for making sure all nodes that can be jumped to 
    // have a unique label.
    println "visitNode:"
    println "$stmt"
    
    stmt.getPredecessors().each { p ->
      if(p instanceof LowIrJump) {
        if(p.jmpTrueDest.is(stmt) || p.jmpFalseDest.is(stmt)) {
          // Somenode can jump to this node!
          stmt.TryAssignUniqueLabel()
        }
      }
    }
    
    // If stmt has a unique-label, then we should print out a 
    // label in the assembler.
    if(stmt.HasUniqueAssignedLabel()) {
      // print out a label...
      emit(stmt.uniqueLabel+":")
    }

    // The rest of the code...

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
    case LowIrJump:
      // First make sure that both branches have unique labels.
      stmt.jmpTrueDest.TryAssignUniqueLabel()
      stmt.jmpFalseDest.TryAssignUniqueLabel()

      //cmp(getTmp(stmt.tmpVar), 0)
      cmp(0, getTmp(stmt.tmpVar))
      // jne(getLabel(stmt.jmpTrueDest.uniqueLabel))
      // je(getLabel(stmt.jmpFalseDest.uniqueLabel))
      jne(stmt.jmpTrueDest.uniqueLabel)
      je(stmt.jmpFalseDest.uniqueLabel)
      break;
    case LowIrMethodCall:
      stmt.paramTmpVars.each { push(getTmp(it)) }
      call(stmt.descriptor.name)
      movq(rax,getTmp(stmt.tmpVar))
      add(8*stmt.paramTmpVars.size(), rsp)
      break
    case LowIrReturn:
      if (stmt.tmpVar != null) {
        movq(getTmp(stmt.tmpVar),rax)
      }
      leave()
      ret()
      break
    case LowIrMov:
      if (stmt.src.type != TempVarType.ARRAY) {
        movq(getTmp(stmt.src), r10)
      } else {
        movq(getTmp(stmt.src.arrayIndexTmpVar), r11)
        def arrOp = r11(stmt.src.globalName, 8)
        movq(arrOp, r10)
      }
      if (stmt.dst.type != TempVarType.ARRAY) {
        movq(r10, getTmp(stmt.dst))
      } else {
        movq(getTmp(stmt.dst.arrayIndexTmpVar), r11)
        def arrOp = r11(stmt.dst.globalName, 8)
        movq(r10, arrOp)
      }
      break
    case LowIrBinOp:
      switch (stmt.op) {
      case ADD:
        movq(getTmp(stmt.leftTmpVar),r10)
        movq(getTmp(stmt.rightTmpVar),r11)
        add(r10,r11)
        movq(r11,getTmp(stmt.tmpVar))
        break
      case SUB:
        movq(getTmp(stmt.leftTmpVar),r10)
        movq(getTmp(stmt.rightTmpVar),r11)
        sub(r10,r11)
        movq(r11,getTmp(stmt.tmpVar))
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
        throw new RuntimeException("still haven't implemented that yet")
      }
      break
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
    println "hit emit!:"
    println "$s"
    asm[region] << s
  }

  void link(GraphNode src, GraphNode dst) {
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
