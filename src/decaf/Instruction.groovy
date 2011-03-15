package decaf

class Instruction {
  InstrType type
  Operand op1
  Operand op2

  Instruction(InstrType type) {
    this.type = type
  }

  Instruction(InstrType type, Operand op1) {
    this.type = type
    this.op1 = op1
  }

  Instruction(InstrType type, Operand op1, Operand op2) {
    this.type = type
    this.op1 = op1
    this.op2 = op2
  }

  String getOpCode() {
    switch (type.numOperands) {
    case 2:
      assert op2 != null
    case 1:
      assert op1 != null
      break
    case 0:
      assert op1 == null
      break
    default:
      assert false
    }

    switch (type.numOperands) {
    case 0:
      return "$type.name"
    case 1:
      return "$type.name $op1"
    case 2:
      return "$type.name $op1, $op2"
    }
  }

  String toString() { getOpCode() }
}

enum InstrType {
//copying values
  MOV('movq', 2),
  CMOVE('cmove',2),
  CMOVNE('cmovne',2),
  CMOVG('cmovg',2),
  CMOVL('cmovl',2),
  CMOVGE('cmovge',2),
  CMOGLE('cmovle',2),

//Stack Management
  ENTER('enter',2),
  LEAVE('leave',0),
  PUSH('push',1),
  POP('pop',1),

//Control Flow
  CALL('call', 1),
  RET('ret',0),
  JMP('jmp',1),
  JE('je',1),
  JNE('jne',1),

//ARITHMETIC AND LOGIC
  ADD('add',2),
  SUB('sub',2),
  IMUL('imul',2),
  IDIV('idiv',1),
  SHR('shr',1),
  SHL('shl',1),
  ROR('ror',2),
  CMP('cmp',2);

  final String name
  final int numOperands

  InstrType(String name, int numOperands) {
    this.name = name
    this.numOperands = numOperands
  }
}

class Operand {
  OperType type
  def val
  def offset
  def stride

  Operand(int val) {
    this.type = OperType.IMM
    this.val = val
  }

  Operand(Reg val) {
    this.type = OperType.REG
    this.val = val
  }

  Operand(String val) {
    this.type = OperType.ADDR
    this.val = val
  }

  Operand(String offset, Reg val) {
    this.type = OperType.MEM
    this.val = val
    this.offset = offset
  }

  Operand(int offset, Reg val) {
    this.type = OperType.MEM
    this.val = val
    this.offset = offset
  }

  String toString() {
    assert val != null
    switch (type) {
    case OperType.IMM:
      return "\$${val}"
    case OperType.REG:
      assert val instanceof Reg
      return "%${val.name}"
    case OperType.ADDR:
      return "${val}"
    case OperType.MEM:
      assert offset != null
      assert val instanceof Reg
      if (stride == null) {
        return "${offset}(%${val.name})"
      } else {
        return "${offset}(,%${val.name},${stride})"
      }
    }
  }
}

enum OperType {
  ADDR,
  IMM,
  REG,
  MEM
}

enum Reg {
  RAX('rax'),
  RBX('rbx'),
  RCX('rcx'),
  RDX('rdx'),
  RSP('rsp'),
  RBP('rbp'),
  RSI('rsi'),
  RDI('rdi'),
  R8('r8'),
  R9('r9'),
  R10('r10'),
  R11('r11'),
  R12('r12'),
  R13('r13'),
  R14('r14'),
  R15('r15');

  final String name

  Reg(String name) {
    this.name = name
  }
} 
