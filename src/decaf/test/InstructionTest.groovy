package decaf.test
import decaf.*

class InstructionTest extends GroovyTestCase {

  void testInstrCreation() {
    Instruction instr1 = new Instruction(InstrType.MOV, new Operand(3), new Operand(Reg.RAX))
    assertEquals('mov $3, %rax',instr1.getOpCode())

    Instruction instr2 = new Instruction(InstrType.ENTER, new Operand(48), new Operand(0))
    assertEquals('enter $48, $0', instr2.getOpCode())

    Instruction instr3 = new Instruction(InstrType.LEAVE)
    assertEquals('leave', instr3.getOpCode())

    Instruction instr4 = new Instruction(InstrType.PUSH, new Operand(Reg.RSP))
    assertEquals('push %rsp', instr4.getOpCode())

    Instruction instr5 = new Instruction(InstrType.POP, new Operand('0x8000'))
    assertEquals('pop 0x8000', instr5.getOpCode())

    Instruction instr6 = new Instruction(InstrType.CALL, new Operand('printf'))
    assertEquals('call printf', instr6.getOpCode())

    Instruction instr7 = new Instruction(InstrType.RET)
    assertEquals('ret', instr7.getOpCode())

    Instruction instr8 = new Instruction(InstrType.JMP, new Operand('.helloWorld'))
    assertEquals('jmp .helloWorld', instr8.getOpCode())

    Instruction instr9 = new Instruction(InstrType.JE, new Operand(Reg.RCX))
    assertEquals('je %rcx', instr9.getOpCode())

    Instruction instr10 = new Instruction(InstrType.JNE, new Operand(Reg.RBX))
    assertEquals('jne %rbx', instr10.getOpCode())

    Instruction instr11 = new Instruction(InstrType.ADD, new Operand(Reg.R10), new Operand(-8, Reg.RBP))
    assertEquals('add %r10, -8(%rbp)', instr11.getOpCode())

    Instruction instr12 = new Instruction(InstrType.SUB, new Operand(Reg.RSI), new Operand(Reg.RDI))
    assertEquals('sub %rsi, %rdi', instr12.getOpCode())
  }
}
