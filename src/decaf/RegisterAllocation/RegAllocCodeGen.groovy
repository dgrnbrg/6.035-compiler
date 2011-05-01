package decaf
import decaf.graph.*
import static decaf.BinOpType.*
import static decaf.Reg.eachRegNode

class RegAllocCodeGen extends CodeGenerator {

  RegAllocCodeGen() {
    paramRegs = [rdi, rsi, rdx, rcx, r8, r9]
  }

  void handleMethod(MethodDescriptor method) {
    println "USING REGALLOC CODEGEN!"
    this.method = method
    asmMacro('.globl', method.name)
    emit(method.name + ':')
    enter(8*(method.params.size() + method.svManager.getNumSpillVarsToAllocate()),0)
    traverseWithTraces(method.lowir)
  }

  Operand getTmp(TempVar tmp) {
    switch (tmp.type) {
    case TempVarType.PARAM:
      return rbp(8 * (tmp.id+2))
    case TempVarType.SPILLVAR:
      assert tmp instanceof SpillVar;
      return rbp(-8 * (1 + method.svManager.getLocOfSpillVar(tmp)));
    case TempVarType.REGISTER:
      assert tmp instanceof RegisterTempVar;
      return new Operand(Reg.getReg(tmp.registerName));
    case TempVarType.LOCAL: 
      println tmp;
      assert false; // We no longer have "locals".
    default:
      assert false
    }
  }

  void PreserveRegister(Reg r) {
    movq(r, getTmp(method.svManager.getPreservedSpillVarFor(r.toString())));
  }

  void RestoreRegister(Reg r) {
    movq(getTmp(method.svManager.getPreservedSpillVarFor(r.toString())), r);
  }

  void PreserveCallerRegisters() {
    Reg.GetCallerSaveRegisters().each { PreserveRegister(it); }
  }

  void RestoreCallerRegisters() {
    Reg.GetCallerSaveRegisters().each { RestoreRegister(it); }
  }

  void PreserveCalleeRegisters() {
    Reg.GetCalleeSaveRegisters().each { PreserveRegister(it); }
  }

  void RestoreCalleeRegisters() {
    Reg.GetCalleeSaveRegisters().each { RestoreRegister(it); }
  }

  void ValidateFirstSixArgumentsAndReturnRegisters(LowIrNode stmt) {
    assert (stmt instanceof LowIrMethodCall) || (stmt instanceof LowIrCallOut);
    int numRegParams = Math.min(stmt.paramTmpVars.size(), 6)
    stmt.paramTmpVars.eachWithIndex { ptv, i -> 
      if(i < numRegParams) {
        assert ptv instanceof RegisterTempVar;
        assert Reg.GetParameterRegisters().contains(Reg.getReg(ptv.registerName));
      } 
    }

    if(stmt.getDef() instanceof RegisterTempVar)
      assert stmt.getDef().registerName == 'rax';
  }

  void visitNode(GraphNode stmt) {
    def predecessors = stmt.getPredecessors()
    def successors = stmt.getSuccessors()

    //assert no X nodes, only ^ (branch) or V nodes (join) 
    if(predecessors.size() > 1) assert successors.size()   <= 1
    if(successors.size()   > 1) assert predecessors.size() <= 1

    if(stmt.anno["trace"]["start"] || stmt.anno["trace"]["JmpDest"])
      emit(stmt.label + ':')

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
      // Both CallOuts and MethodCalls use same calling convention.
      ValidateFirstSixArgumentsAndReturnRegisters(stmt);
      PreserveCallerRegisters();

      // Now optionally preserve rax if it is still a tempvar (because it wasn't colored).
      if(!(stmt.tmpVar instanceof RegisterTempVar || stmt.tmpVar instanceof SpillVar))
        PreserveRegister(Reg.RAX);

      int numRegParams = Math.min(stmt.paramTmpVars.size(), 6)
      if(stmt.paramTmpVars.size() - 6 > 0)
        sub(8*(stmt.paramTmpVars.size() - 6), rsp);
      stmt.paramTmpVars.eachWithIndex { it, index ->
        if(index >= numRegParams) {
          movq(getTmp(it), r10)
          movq(r10, rsp(8*(index - numRegParams)))
        }
      }
      // We need to restore r10 since we've been clobbering it.
      RestoreRegister(Reg.R10);

      if(stmt instanceof LowIrCallOut) {
        // Set to 0 since printf uses rax value to determine how many SSE 
        // registers hold arguments (since printf has varargs).
        if (stmt.name == 'printf')         
          movq(0,rax)
        call(stmt.name);
      } else
        call(stmt.descriptor.name)

      // Now optionally don't do the mov (since you shouldn't encounter that tmpVar anyway).
      if(stmt.tmpVar instanceof RegisterTempVar || stmt.tmpVar instanceof SpillVar)
        movq(rax,getTmp(stmt.tmpVar))
      else
        println "Don't have a return value to worry about!"

      RestoreCallerRegisters();

      // Now optionally restore rax if it wasn't clobbered.
      if(!(stmt.tmpVar instanceof RegisterTempVar || stmt.tmpVar instanceof SpillVar))
        RestoreRegister(Reg.RAX);

      if(stmt.paramTmpVars.size() - 6 > 0)
        add(8*(stmt.paramTmpVars.size() - 6), rsp)

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
      assert false;
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
      if(stmt.src instanceof SpillVar || stmt.dst instanceof SpillVar) {
        assert false; // we should never be moving directly between spillvars.
      } else if(stmt.src instanceof SpillVar || stmt.dst instanceof SpillVar) {
        movq(getTmp(stmt.src), getTmp(stmt.dst))
        break;
      } else if(stmt.src instanceof RegisterTempVar && stmt.dst instanceof RegisterTempVar) {
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
      assert false;
      assert stmt.storeLoc instanceof SpillVar
      movq(getTmp(stmt.value), getTmp(stmt.storeLoc));
      break;
    case LowIrLoadSpill:
      assert false;
      assert stmt.loadLoc instanceof SpillVar;
      movq(getTmp(stmt.loadLoc), getTmp(stmt.tmpVar));
      break;
    case LowIrNode: //this is a noop
      assert stmt.getClass() == LowIrNode.class || stmt.getClass() == LowIrValueNode.class
      break
    default:
      assert false
    }

    if(stmt.anno["trace"]["FalseJmpSrc"])
      jmp(stmt.falseDest.label)
    else if(stmt.anno["trace"]["JmpSrc"])
      jmp(stmt.anno["trace"]["JmpSrc"])
  }
}
