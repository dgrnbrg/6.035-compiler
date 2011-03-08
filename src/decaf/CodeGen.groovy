package decaf
import decaf.graph.*

class CodeGenerator extends Traverser {
  def asm = []
  def paramRegs = []

  CodeGenerator() {
    paramRegs = [rdi, rsi, rdx, rcx, r8, r9]
  }

  void visitNode(GraphNode cur) {
    assert cur instanceof LowIrBlock
    cur.statements.each { stmt ->
      switch (stmt) {
      case Allocate:
        //allocate quadword on the stack
        sub(rsp,8)
        break
      case Callout:
        if (stmt.name == 'printf') {
          stmt.arguments.add(0,0) //must have 0 in rax
        }
        arguments.eachWithIndex {arg, index ->
          //could be stringliteral(.value) or descriptor
          if (arg instanceof StringLiteral) {
            mov(asmString(arg.value), paramRegs[index])
          } else if (arg instanceof VariableDescriptor) {
            mov(addrOf(arg), paramRegs[index])
          }
        }
        call(stmt.name)
        break
      }
    }
  }

  def asmString(String s) {
    def label = genPrivateLabel()
    asmMacro('.string', "\"$s\"")
    return label
  }

  def asmMacro(String macro, arg) {
    String result = macro + ' ' + arg
    emit(result)
  }

  int privateLabelIndex = 0
  def genPrivateLabel() {
    def label = ".label${privateLabelIndex++}".toString()
    emit(label + ':')
    return new Operand(type: OperType.ADDR, val: label)
  }

  def emit(s) {
    asm << s
  }

  void link(GraphNode src, GraphNode dst) {
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
    }
  }

  def propertyMissing(String name) {
    if (nameToReg.containsKey(name)) {
      return new Operand(nameToReg[name])
    }
  }
}

cg = new CodeGenerator()
/*
cg.sub(cg.rax, 8)
cg.add(cg.rax(3), cg.r10)
*/
lowirgen = new LowIrGenerator()
hb = new HiIrBuilder()
prog = hb.Block{
  CallOut('printf', 'hello world')
}
cg.traverse(gen.handleStatement(prog).begin)
cg.asm.each {println it.getOpCode()}
