package decaf.RegisterAllocation

import decaf.*
import decaf.graph.*
import static decaf.BinOpType.*
import static decaf.Reg.eachRegNode
import decaf.optimizations.*

class RegAllocCodeGen extends CodeGenerator {

  RegAllocCodeGen() {
    paramRegs = [rdi, rsi, rdx, rcx, r8, r9]
  }

  void Comment(String comment) {
    emit("                        // " + comment);
  }

  void handleMethod(MethodDescriptor method) {
    this.method = method
    asmMacro('.globl', method.name)
    emit(method.name + ':')
    enter(8*(method.params.size() + method.svManager.getNumSpillVarsToAllocate()),0)
    PreserveCalleeRegisters();

    // Do we need to load any of the first six arguments into spillvars?
    assert method.svManager.firstSixFlags != null
    if(method.svManager.firstSixFlags.size() > 0) {
      method.svManager.firstSixFlags.keySet().each { tv -> 
        //println method.svManager.firstSixFlags
        Comment("load reg-args into spillvars for tv = $tv, sv = ${method.svManager.firstSixFlags[tv]}");
        assert tv.id >= 0 && tv.id < 6;
        movq(paramRegs[tv.id], getTmp(method.svManager.firstSixFlags[tv]));
      }
    }

    // Do we need to load any of the post six arguments into registers?
    assert method.svManager.postSixColorFlags != null
    if(method.svManager.postSixColorFlags.size() > 0) {
      method.svManager.postSixColorFlags.keySet().each { ptv -> 
        Comment("load post-six args into registers for ptv = $ptv, reg = ${method.svManager.postSixColorFlags[ptv]}")
        //println method.svManager.postSixColorFlags
        movq(GetPostSixParamTmp(ptv), method.svManager.postSixColorFlags[ptv]);
      }
    }

    traverseWithTraces(method.lowir)
  }

  Operand GetPostSixParamTmp(TempVar tmp) {
    assert tmp.id >= 6;
    return rbp(8 * ((tmp.id - 6) + 2));
  }

  Operand getTmp(TempVar tmp) {
    switch (tmp.type) {
    case TempVarType.PARAM:
      assert false;
    case TempVarType.SPILLVAR:
      assert tmp instanceof SpillVar;
      if(tmp instanceof PostSixParamSpillVar)
        return GetPostSixParamTmp(tmp);
      else
        return rbp(-8 * (1 + method.svManager.getLocOfSpillVar(tmp)));
    case TempVarType.REGISTER:
      assert tmp instanceof RegisterTempVar;
      return new Operand(Reg.getReg(tmp.registerName));
    case TempVarType.LOCAL: 
      //println tmp;
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
    assert (stmt instanceof LowIrMethodCall) || (stmt instanceof LowIrCallOut)
    assert stmt.paramTmpVars.size() <= 6;
    stmt.paramTmpVars.eachWithIndex { ptv, i -> 
      assert ptv instanceof RegisterTempVar;
      assert Reg.GetParameterRegisters().contains(Reg.getReg(ptv.registerName));
    }
  }

  void visitNode(GraphNode stmt) {

    // The extra tabs make it more readable.
    if(!(stmt.class == LowIrValueNode)) {
      if(stmt instanceof LowIrMov && stmt.src != stmt.dst)
        Comment("$stmt");
      else if(!(stmt instanceof LowIrMov))
        Comment "$stmt"
    }

    def predecessors = stmt.getPredecessors()
    def successors = stmt.getSuccessors()

    //assert no X nodes, only ^ (branch) or V nodes (join) 
    if(predecessors.size() > 1) assert successors.size()   <= 1
    if(successors.size()   > 1) assert predecessors.size() <= 1

    if(stmt.anno["trace"]["start"] || stmt.anno["trace"]["JmpDest"]) {
      emit('.p2align 4,,10')
      emit('.p2align 3')
      emit(stmt.label + ':')
    }

    switch (stmt) {
    case LowIrStringLiteral:
      def strLitOperand = asmString(stmt.value)
      strLitOperand.type = OperType.IMM
      movq(strLitOperand, getTmp(stmt.tmpVar))
      break
    case LowIrIntLiteral:
      assert stmt.tmpVar instanceof RegisterTempVar
      if(stmt.useless == false) {
        if(stmt.value == 0)
          xor(getTmp(stmt.tmpVar), getTmp(stmt.tmpVar));
        else
          movq(new Operand(stmt.value), getTmp(stmt.tmpVar))
      }
      break
    case LowIrBoundsCheck:
      doBoundsCheck(stmt.lowerBound, stmt.upperBound, getTmp(stmt.testVar), stmt.desc.name)
      break
    case LowIrCallOut:
    case LowIrMethodCall:
      ValidateFirstSixArgumentsAndReturnRegisters(stmt);
      PreserveCallerRegisters();

      // Now optionally preserve rax if it is still a tempvar (because it wasn't colored).
      //if(!(stmt.tmpVar instanceof RegisterTempVar))
      //  PreserveRegister(Reg.RAX);

      if(stmt instanceof LowIrCallOut) {
        // Set to 0 since printf uses rax value to determine how many SSE 
        // registers hold arguments (since printf has varargs).
        if (stmt.name == 'printf')         
          movq(0,rax)
        call(stmt.name);
      } else
        call(stmt.descriptor.name)

      RestoreCallerRegisters();

      // Now optionally don't do the mov (since you shouldn't encounter that tmpVar anyway).
      if(stmt.tmpVar instanceof RegisterTempVar)
        assert stmt.tmpVar.registerName == 'rax'

      // Now optionally restore rax if it wasn't clobbered.
      //if(!(stmt.tmpVar instanceof RegisterTempVar))
      //  RestoreRegister(Reg.RAX);

      if(stmt.numOriginalArgs - 6 > 0)
        add(8*(stmt.numOriginalArgs - 6), rsp)

      break
    case LowIrLoadArgOntoStack: 
      sub(8, rsp);
      movq(getTmp(stmt.arg), rsp(0))
      break
    case LowIrReturn:
      if (stmt.tmpVar != null) {
        assert stmt.tmpVar instanceof RegisterTempVar
        if(stmt.tmpVar.registerName != 'rax')
          movq(getTmp(stmt.tmpVar),rax)
      }
      else
        movq(0,rax) //void fxns return 0
      RestoreCalleeRegisters();
      leave()
      ret()
      break
    case LowIrCondCoalesced:
      assert stmt.leftTmpVar instanceof RegisterTempVar;
      assert stmt.rightTmpVar instanceof RegisterTempVar;
      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
      switch (stmt.op) {
      case GT:
        jg(stmt.trueDest.label)
	      break
      case LT:
	      jl(stmt.trueDest.label)
	      break
      case LTE:
	      jle(stmt.trueDest.label)
	      break
      case GTE:
	      jge(stmt.trueDest.label)
	      break
      case EQ:
	      je(stmt.trueDest.label)
	      break
      case NEQ:
	      jne(stmt.trueDest.label)
	      break
      default:
        assert false;
      }
      break;
    case LowIrCondJump:
      assert stmt.condition instanceof RegisterTempVar;
      cmp(1, getTmp(stmt.condition))
      je(stmt.trueDest.label)
      break
    case LowIrLoad:
      if (stmt.index != null) {
        movq(getTmp(stmt.index), r10)
        def arrOp = r10(stmt.desc.name + '_globalvar', 4)
        movsxl(arrOp, getTmp(stmt.tmpVar))
      } else {
        movsxl(new Operand(stmt.desc.name + '_globalvar'), getTmp(stmt.tmpVar))
      }
      break
    case LowIrStore:
      if (stmt.index != null) {
        movq(getTmp(stmt.index), r10)
        def arrOp = r10(stmt.desc.name + '_globalvar', 4)
        mov(Reg.get32BitReg(getTmp(stmt.value)), arrOp)
      } else {
        mov(Reg.get32BitReg(getTmp(stmt.value)), new Operand(stmt.desc.name + '_globalvar'))
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
      }
      assert false;
      break;
    case LowIrRightCurriedOp:
      // The right operand of these binops are constants
//println "###################################################################################"
      assert stmt.tmpVar instanceof RegisterTempVar;
      assert stmt.input instanceof RegisterTempVar;
      switch (stmt.op) {
      case ADD:
        emit("// LOL: $stmt")
        if(stmt.input == stmt.tmpVar) {
          add(stmt.constant, getTmp(stmt.input));
        } else {
          movq(stmt.constant, getTmp(stmt.tmpVar));
          add(getTmp(stmt.input), getTmp(stmt.tmpVar));
        }
        break;
      case SUB:
        if(stmt.input == stmt.tmpVar) {
          sub(stmt.constant, getTmp(stmt.tmpVar));
        } else {
          movq(getTmp(stmt.input), getTmp(stmt.tmpVar));
          sub(stmt.constant, getTmp(stmt.tmpVar));
        }
        break
      case MUL:
        //if(0 <= stmt.constant && stmt.constant < 10) {
        
        //}
        if(stmt.input == stmt.tmpVar) {
          imul(stmt.constant, getTmp(stmt.input));
        } else {
          movq(stmt.constant, getTmp(stmt.tmpVar));
          imul(getTmp(stmt.input), getTmp(stmt.tmpVar));
        }
        break
      case DIV:
        assert stmt.tmpVar.registerName == 'rax'
        assert stmt.input.registerName == 'rax'
        def log2 = { n -> 
          if (n <= 0) throw new IllegalArgumentException()
          return 31 - Integer.numberOfLeadingZeros(n)
        }
        //println "Carrying out special division operation."
        def e = stmt.constant
        def d = Math.abs(stmt.constant)
        if(e == 1) {
          movq(getTmp(stmt.input), getTmp(stmt.tmpVar));
        } else if (e == -1) {
          //println "Special Div took first if."
          neg(rax)
        } else if (d == 2) {
          //println "Special Div took second if."
          //todo: test
          cmp(0x80000000, rax)
          sbb(-1, rax)
          sar(1, rax)
          if(e < 0) 
            neg(rax)
        } else if (!(d & (d - 1))) {
          //println "SpecialDiv took third if."
          //assert false; // We need to make sure this line was tested, so the assert is here as a watch.
          cqo()
          and(d-1, rdx)
          add(rdx, rax)
          if(log2(d) != 0) 
            sar(log2(d), rax)
          if(e < 0) 
            neg(rax)
        } else {
          cqo()
          push(rcx)
          movq(stmt.constant, rcx)
          idiv(rcx)
          pop(rcx)
        }
        break;
      case MOD:
        assert stmt.tmpVar.registerName == 'rdx'
        assert stmt.input.registerName == 'rax'
        
        // Here is where you should put the mod 4 peephole code.
        if(!(stmt.constant & (stmt.constant - 1)) && stmt.constant >= 0) {
          and(stmt.constant - 1, getTmp(stmt.input));
          movq(rax, rdx);
        } else {
          cqo()
          push(rcx)
          movq(stmt.constant, rcx);
          idiv(rcx)
          pop(rcx)
        }
        break;
      default:
        throw new RuntimeException("still haven't implemented that yet: $stmt $stmt.op")
      }
      break
    case LowIrLeftCurriedOp:
      // The left operand of these binops are constants
      assert stmt.tmpVar instanceof RegisterTempVar;
      assert stmt.input instanceof RegisterTempVar;
//println "###################################################################################"
      switch (stmt.op) {
      case ADD:
        if(stmt.input == stmt.tmpVar) {
          add(stmt.constant, getTmp(stmt.input));
        } else {
          movq(stmt.constant, getTmp(stmt.tmpVar));
          add(getTmp(stmt.input), getTmp(stmt.tmpVar));
        }
        break;
      case SUB:
        if(stmt.input == stmt.tmpVar) {
          neg(getTmp(stmt.tmpVar))
          add(stmt.constant, getTmp(stmt.tmpVar));
        } else {
          movq(stmt.constant, getTmp(stmt.tmpVar));
          sub(getTmp(stmt.input), getTmp(stmt.tmpVar));
        }
        break
      case MUL:
        if(stmt.input == stmt.tmpVar) {
          imul(stmt.constant, getTmp(stmt.input));
        } else {
          movq(stmt.constant, getTmp(stmt.tmpVar));
          imul(getTmp(stmt.input), getTmp(stmt.tmpVar));
        }
        break
      case DIV:
        assert stmt.tmpVar.registerName == 'rax'
        assert stmt.input instanceof RegisterTempVar
        assert stmt.input.registerName != 'rax'
        movq(stmt.constant, rax);
        cqo();
        idiv(getTmp(stmt.input));
        break;
      case MOD:
        assert stmt.tmpVar.registerName == 'rdx'
        assert stmt.input instanceof RegisterTempVar
        assert stmt.input.registerName != 'rax'
        movq(stmt.constant, rax);
        cqo()
        idiv(getTmp(stmt.input))
        break;
      default:
        throw new RuntimeException("still haven't implemented that yet: $stmt $stmt.op")
      }
      break
    case LowIrBinOp:
      assert stmt.tmpVar instanceof RegisterTempVar;
      assert stmt.leftTmpVar instanceof RegisterTempVar;
      switch (stmt.op) {
      case GT:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, r10)
	      movq(0, getTmp(stmt.tmpVar))
	      cmovg(r10, getTmp(stmt.tmpVar))
	      break
      case LT:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, r10)
	      movq(0, getTmp(stmt.tmpVar))
	      cmovl(r10, getTmp(stmt.tmpVar))
	      break
      case LTE:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, r10)
	      movq(0, getTmp(stmt.tmpVar))
	      cmovle(r10, getTmp(stmt.tmpVar))
	      break
      case GTE:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, r10)
	      movq(0, getTmp(stmt.tmpVar))
	      cmovge(r10, getTmp(stmt.tmpVar))
	      break
      case EQ:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, r10)
	      movq(0, getTmp(stmt.tmpVar))
	      cmove(r10, getTmp(stmt.tmpVar))
	      break
      case NEQ:
	      cmp(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar))
	      movq(1, r10)
	      movq(0, getTmp(stmt.tmpVar))
	      cmovne(r10, getTmp(stmt.tmpVar))
	      break
      case NOT:
        movq(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar))
	      xor(1, getTmp(stmt.tmpVar))
	      break
      case ADD:
        emit("// LOL: $stmt")
        if(stmt.leftTmpVar == stmt.tmpVar && stmt.rightTmpVar == stmt.tmpVar) {
          add(getTmp(stmt.leftTmpVar), getTmp(stmt.rightTmpVar));
        } else if(stmt.leftTmpVar == stmt.tmpVar) {
          add(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar));
        } else if(stmt.rightTmpVar == stmt.tmpVar) {
          add(getTmp(stmt.leftTmpVar), getTmp(stmt.rightTmpVar));
        } else {
          movq(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar));
          add(getTmp(stmt.rightTmpVar), getTmp(stmt.tmpVar));
        }
        break;
      case SUB:
        if(stmt.leftTmpVar == stmt.tmpVar && stmt.rightTmpVar == stmt.tmpVar) {
          // The following line should probably be replaced by:
          // movq($0, getTmp(stmt.tmpVar));
          sub(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar));
        } else if(stmt.leftTmpVar == stmt.tmpVar) {
          sub(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar));
        } else if(stmt.rightTmpVar == stmt.tmpVar) {
          assert false;
        } else {
          movq(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar));
          sub(getTmp(stmt.rightTmpVar), getTmp(stmt.tmpVar));
        }
        break
      case MUL:
        if(stmt.leftTmpVar == stmt.tmpVar && stmt.rightTmpVar == stmt.tmpVar) {
          imul(getTmp(stmt.leftTmpVar), getTmp(stmt.rightTmpVar));
        } else if(stmt.leftTmpVar == stmt.tmpVar) {
          imul(getTmp(stmt.rightTmpVar), getTmp(stmt.leftTmpVar));
        } else if(stmt.rightTmpVar == stmt.tmpVar) {
          imul(getTmp(stmt.leftTmpVar), getTmp(stmt.rightTmpVar));
        } else {
          movq(getTmp(stmt.leftTmpVar), getTmp(stmt.tmpVar));
          imul(getTmp(stmt.rightTmpVar), getTmp(stmt.tmpVar));
        }
        break
      case DIV:
        assert stmt.tmpVar.registerName == 'rax'
        assert stmt.leftTmpVar.registerName == 'rax'
        cqo()
        idiv(getTmp(stmt.rightTmpVar))
        break
      case MOD:
        assert stmt.tmpVar.registerName == 'rdx'
        assert stmt.leftTmpVar.registerName == 'rax'
        cqo()
        idiv(getTmp(stmt.rightTmpVar))
        break
      default:
        throw new RuntimeException("still haven't implemented that yet: $stmt $stmt.op")
      }
      break
    case LowIrPhi: assert false
      break
    case LowIrCopyArray:
      push(rax)
      xor(rax, rax)
      def copyLabel = genPrivateLabel()
      movdqa(rax(stmt.src.name+'_globalvar'), xmm0)
      movdqa(xmm0, rax(stmt.dst.name+'_globalvar'))
      add(16, rax)
      cmp(stmt.dst.arraySize*4, rax)
      jne(copyLabel)
      break
    case LowIrParallelizedLoop:
      Reg.GetCallerSaveRegisters().each{push(it)}
      //pointer to thread function
      def threadFuncOperand = new Operand(stmt.func.name)
      threadFuncOperand.type = OperType.IMM
      def threadPrefix = "${stmt.func.name}_threadid"
      emit('bss', ".comm ${threadPrefix}0, 8, 16")
      emit('bss', ".comm ${threadPrefix}1, 8, 16")
      emit('bss', ".comm ${threadPrefix}2, 8, 16")
      emit('bss', ".comm ${threadPrefix}3, 8, 16")
      //globals storing thread ids
      def thread0id = new Operand("${threadPrefix}0")
      thread0id.type = OperType.IMM
      def thread1id = new Operand("${threadPrefix}1")
      thread1id.type = OperType.IMM
      def thread2id = new Operand("${threadPrefix}2")
      thread2id.type = OperType.IMM
      def thread3id = new Operand("${threadPrefix}3")
      thread3id.type = OperType.IMM
      //set up args
      movq(0, rcx)
      movq(threadFuncOperand, rdx)
      movq(0, rsi)
      movq(thread0id, rdi)
      call('pthread_create')
      movq(1, rcx)
      movq(threadFuncOperand, rdx)
      movq(0, rsi)
      movq(thread1id, rdi)
      call('pthread_create')
      movq(2, rcx)
      movq(threadFuncOperand, rdx)
      movq(0, rsi)
      movq(thread2id, rdi)
      call('pthread_create')
      movq(3, rcx)
      movq(threadFuncOperand, rdx)
      movq(0, rsi)
      movq(thread3id, rdi)
      call('pthread_create')
      movq(rip("${threadPrefix}0"), rax)
      movq(0, rsi)
      movq(rax, rdi)
      call('pthread_join')
      movq(rip("${threadPrefix}1"), rax)
      movq(0, rsi)
      movq(rax, rdi)
      call('pthread_join')
      movq(rip("${threadPrefix}2"), rax)
      movq(0, rsi)
      movq(rax, rdi)
      call('pthread_join')
      movq(rip("${threadPrefix}3"), rax)
      movq(0, rsi)
      movq(rax, rdi)
      call('pthread_join')
      Reg.GetCallerSaveRegisters().reverse().each{pop(it)}
      break
    case LowIrStoreSpill:
      assert stmt.storeLoc instanceof SpillVar
      movq(getTmp(stmt.value), getTmp(stmt.storeLoc));
      break;
    case LowIrLoadSpill:
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
