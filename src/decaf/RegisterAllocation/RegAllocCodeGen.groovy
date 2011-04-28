package decaf
import decaf.graph.*
import static decaf.BinOpType.*

class RegAllocCodeGen extends CodeGenerator {

  List<String> callerSaveRegisters = ['rcx', 'rdx', 'rsi', 'rdi', 'r8', 'r9', 'r10', 'r11'];
  List<String> calleeSaveRegisters = ['rbx', 'r12', 'r13', 'r14', 'r15'];

  RegAllocCodeGen() {
    paramRegs = [rdi, rsi, rdx, rcx, r8, r9]
  }

  void handleMethod(MethodDescriptor method) {
    this.method = method
    this.method.svManager.ConstructFinalLineup();
    asmMacro('.globl', method.name)
    emit(method.name + ':')
    enter(8*(method.params.size() + method.svManager.getNumSpillVarsToAllocate()),0)
    traverseWithTraces(method.lowir)
  }

  def registerNameToOperand = 
    ['rax' : rax, 'rbx' : rbx,
     'rcx' : rcx, 'rdx' : rdx,
     'rsp' : rsp, 'rbp' : rbp,
     'rsi' : rsi, 'rdi' : rdi,
     'r8'  : r8,  'r9'  : r9,
     'r10' : r10, 'r11' : r11,
     'r12' : r12, 'r13' : r13,
     'r14' : r14, 'r15' : r15];

  Operand getTmp(TempVar tmp) {
    assert (tmp instanceof SpillVar || tmp instanceof RegisterTempVar)
    switch (tmp.type) {
    case TempVarType.PARAM:
      return rbp(8 * (tmp.id+2))
    case TempVarType.SPILLVAR:
      assert tmp instanceof SpillVar;
      return rbp(-8 * (1 + method.svManager.getLocOfSpillVar(tmp)));
    case TempVarType.REGISTER:
      assert tmp instanceof RegisterTempVar;
      assert registerNameToOperand[(tmp.registerName)];
      return registerNameToOperand[(tmp.registerName)];
    case TempVarType.LOCAL: 
      assert false; // We no longer have "locals".
    default:
      assert false
    }
  }

  void PreserveRegister(String regName) {
    movq(registerNameToOperand[(regName)], getTmp(method.svManager.getPreservedRegister(regName)));
  }

  void RestoreRegister(String regName) {
    movq(getTmp(method.svManager.getPreservedRegister(regName)), registerNameToOperand[(regName)]);
  }

  void PreserveCallerRegisters() {
    callerSaveRegisters.each { PreserveRegister(it); }
  }

  void RestoreCallerRegisters() {
    callerSaveRegisters.each { RestoreRegister(it); }
  }

  void PreserveCalleeRegisters() {
    calleeSaveRegisters.each { PreserveRegister(it); }
  }

  void RestoreCalleeRegisters() {
    calleeSaveRegisters.each { RestoreRegister(it); }
  }

  void ValidateFirstSixArgumentsAndReturnRegisters(LowIrNode stmt) {
    assert (stmt instanceof LowIrMethodCall) || (stmt instanceof LowIrCallOut);
    int numRegParams = 6;
    if(stmt.paramTmpVars.size() < numRegParams)
      numRegParams = stmt.paramTmpVars.size();
    stmt.paramTmpVars.eachWithIndex { ptv, i -> 
      if(i < numRegParams) {
        assert ptv instanceof RegisterTempVar;
        assert paramRegs[i] == registerNameToOperand[(ptv.registerName)];
      } 
    }
    assert stmt.getDef() instanceof RegisterTempVar;
    assert stmt.getDef().registerName == 'rax';
  }

  void visitNode(LowIrNode stmt) {
    def predecessors = stmt.getPredecessors()
    def successors = stmt.getSuccessors()

    //assert no X nodes, only ^ (branch) or V nodes (join) 
    if (predecessors.size() > 1)
      assert successors.size() <= 1
    if (successors.size() > 1)
      assert predecessors.size() <= 1

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
    case LowIrMethodCall:
      // Both CallOuts and MethodCalls use same convention.
      int numRegParams = paramRegs.size();
      ValidateFirstSixArgumentsAndReturnRegisters(stmt);
      PreserveCallerRegisters();
      if(stmt.paramTmpVars.size() - numRegParams > 0)
        sub(8*stmt.paramTmpVars.size(), rsp);
      stmt.paramTmpVars.eachWithIndex { it, index ->
        if(index >= numRegParams) {
          movq(getTmp(it), r10)
          movq(r10, rsp(8*(index - numRegParams)))
        }
      }
      // We need to restore r10 since we've been clobbering it.
      RestoreRegister('r10');
      if(stmt instanceof LowIrMethodCall) {
        if (stmt.name == 'printf') {
          // Set to 0 since printf uses rax value to determine how many SSE 
          // registers hold arguments (since printf has varargs).
          movq(0,rax)
        }
        call(stmt.name);
      } else {
        call(stmt.descriptor.name)
      }
      movq(rax,getTmp(stmt.tmpVar))
      RestoreCallerRegisters();
      if(stmt.paramTmpVars.size() - 6 > 0)
        add(8*stmt.paramTmpVars.size(), rsp)
      break
    case LowIrReturn:
      if (stmt.tmpVar != null)
        movq(getTmp(stmt.tmpVar),rax)
      else
        movq(0,rax) //void fxns return 0
      leave()
      ret()
      break
    case LowIrCondJump:
      assert stmt.condition instanceof RegisterTempVar;
      cmp(1, getTmp(stmt.condition))
      je(stmt.trueDest.label)
      break
    case LowIrLoad:
      if (stmt.index != null) {
        movq(getTmp(stmt.index), r11)
        assert false; // How do we handle the line below (the r11 part)
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
      if(stmt.src instanceof SpillVar || stmt.dest instanceof SpillVar) {
        assert false; // we should never be moving directly between spillvars.
      } else if(stmt.src instanceof SpillVar || stmt.dest instanceof SpillVar) {
        movq(getTmp(stmt.src), getTmp(stmt.dst))
        break;
      } else if(stmt.src instanceof RegisterTempVar && stmt.dest instanceof RegisterTempVar) {
        if(stmt.src.registerName != stmt.dst.registerName)
          movq(getTmp(stmt.src), getTmp(stmt.dst));
        break;
      } else {
        assert false; // We should never reach this point.
      }
      break;
    case LowIrBinOp:
      assert stmt.tmpVar instanceof RegisterTempVar;
      assert stmt.leftTmpVar instanceof RegisterTempVar;
      if(stmt.rightTmpVar != null) 
        assert stmt.rightTmpVar instanceof RegisterTempVar;
      switch (stmt.op) {
      case GT:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, getTmp(stmt.leftTmpVar))
	      movq(0, getTmp(stmt.tmpVar))
	      cmovg(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar))
	      break
      case LT:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, getTmp(stmt.leftTmpVar))
	      movq(0, getTmp(stmt.tmpVar))
	      cmovl(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar))
	      break
      case LTE:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, getTmp(stmt.leftTmpVar))
	      movq(0, getTmp(stmt.tmpVar))
	      cmovle(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar))
	      break
      case GTE:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, getTmp(stmt.leftTmpVar))
	      movq(0, getTmp(stmt.tmpVar))
	      cmovge(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar))
	      break
      case EQ:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, getTmp(stmt.leftTmpVar))
	      movq(0, getTmp(stmt.tmpVar))
	      cmove(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar))
	      break
      case NEQ:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, getTmp(stmt.leftTmpVar))
	      movq(0, getTmp(stmt.tmpVar))
	      cmovne(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar))
	      break
      case NOT:
        movq(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar))
	      xor(1, getTmp(stmt.tmpVar))
	      break
      case ADD:
        if(stmt.leftTmpVar != stmt.tmpVar)
          movq(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar));
        add(getTmp(stmt.rightTmpVar), getTmp(stmt.tmpVar));
        break
      case SUB:
        if(stmt.leftTmpVar != stmt.tmpVar)
          movq(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar));
        sub(getTmp(stmt.rightTmpVar), getTmp(stmt.tmpVar));
        break
      case MUL:
        if(stmt.leftTmpVar != stmt.tmpVar)
          movq(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar));
        imul(getTmp(stmt.rightTmpVar), getTmp(stmt.tmpVar));
        break
      case DIV:
        assert stmt.tmpVar.registerName == 'rax'
        movq(0, rdx)
        // these redundant move checks should be removed once we write the 
        // peep-hole optimization for it.
        if(stmt.leftTmpVar.registerName != 'rax')
          movq(getTmp(stmt.leftTmpVar),rax)
        if(stmt.rightTmpVar.registerName != 'r10')
          movq(getTmp(stmt.rightTmpVar),r10)
        idiv(r10)
        if(stmt.tmpVar.registerName != 'rax')
          movq(rax,getTmp(stmt.tmpVar))
        break
      case MOD:
        assert stmt.tmpVar.registerName == 'rdx'
        movq(0, rdx)
        if(stmt.leftTmpVar.registerName != 'rax')
          movq(getTmp(stmt.leftTmpVar),rax)
        if(stmt.rightTmpVar.registerName != 'r10')
          movq(getTmp(stmt.rightTmpVar),r10)
        idiv(r10)
        if(stmt.tmpVar.registerName != 'rdx')
          movq(rdx, getTmp(stmt.tmpVar))
        break
      default:
        throw new RuntimeException("still haven't implemented that yet: $stmt $stmt.op")
      }
      break
    case LowIrPhi:
      break
    case LowIrStoreSpill:
      movq(getTmp(stmt.value), getTmp(stmt.storeLoc));
      break;
    case LowIrLoadSpill:
      movq(getTmp(stmt.storeLoc), getTmp(stmt.tmpVar));
      break;
    case LowIrNode: //this is a noop
      assert stmt.getClass() == LowIrNode.class || stmt.getClass() == LowIrValueNode.class
      break
    default:
      assert false
    }

    if(stmt.anno["trace"]["FalseJmpSrc"]) {
      jmp(stmt.falseDest.label)
    } else if(stmt.anno["trace"]["JmpSrc"]) {
      jmp(stmt.anno["trace"]["JmpSrc"])
    }
  }
}
