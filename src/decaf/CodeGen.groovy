package decaf
import decaf.graph.*
import static decaf.BinOpType.*

class CodeGenerator extends Traverser {
  def asm = [text: ['.text'], strings: ['.data']]
  def paramRegs = []
  MethodDescriptor method

  CodeGenerator() {
    paramRegs = [rdi, rsi, rdx, rcx, r8, r9]
  }

  void handleMethod(MethodDescriptor method, LowIrNode start) {
    this.method = method
    asmMacro('.globl', method.name)
    emit(method.name + ':')
    // enter(8*(method.params.size() + method.maxTmps),0)
    enter(8*(method.params.size() + method.maxTmpVars),0)
    traverse(start)
    leave()
    ret()
  }

  // Operand getTmp(int tmpNum) {
  //   return rbp(-8 * (tmpNum + method.params.size()))
  // }
  Operand getTmp(TempVar tmp){
    return rbp(-8 * ((tmp.getId()+1) + method.params.size()))
  }

  void visitNode(GraphNode stmt) {
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
      // stmt.paramNums.eachWithIndex {tmpVar, index ->
      //   movq(getTmp(tmpVar), paramRegs[index])
      // }
      stmt.paramTmpVars.eachWithIndex {tmpVar, index ->
        movq(getTmp(tmpVar), paramRegs[index])
      }

      if (stmt.name == 'printf') {
        //stmt.params.add(0,0) //must have 0 in rax
        movq(0,rax)
      }
      call(stmt.name)
      break
    case LowIrMethodCall:
      call(stmt.descriptor.name)
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
      assert args.size() == 1
      return new Operand(args[0], nameToReg[name])
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
/*
cg = new CodeGenerator()

cg.sub(cg.rax, 8)
cg.add(cg.rax(3), cg.r10)

lowirgen = new LowIrGenerator()
hb = new HiIrBuilder()
prog = hb.Block{
  CallOut('printf', 'hello world')
}
cg.traverse(gen.handleStatement(prog).begin)
cg.asm.each {println it.getOpCode()}
*/
