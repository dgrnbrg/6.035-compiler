package decaf
import decaf.graph.*
import static decaf.BinOpType.*

class RegAllocCodeGen extends CodeGenerator {

  RegAllocCodeGen() {
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
    assert (tmp instanceof SpillVar || tmp instanceof RegisterTempVar)
    switch (tmp.type) {
    case TempVarType.PARAM:
      return rbp(8 * (tmp.id+2))
    case TempVarType.SPILLVAR:
      assert false // TODO
    case TempVarType.REGISTER:
      assert tmp instanceof RegisterTempVar
      switch(tmp.registerName) {
      case 'rax':
        return rax;
      case 'rbx':
        return rbx;
      case 'rcx':
        return rcx
      default:
        assert false
      }
    case TempVarType.LOCAL: 
      assert false      
      //return rbp(-8 * (tmp.id+1))
    default:
      assert false
    }
  }

  Operand getSpillVar(SpillVar sv) {
    assert false; // Not yet implemented
  }

  void visitNode(GraphNode stmt) {

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
      def paramsOnStack = stmt.paramTmpVars.size() - paramRegs.size()
      if (paramsOnStack > 0)
        sub(8*paramsOnStack, rsp)

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
      if (paramsOnStack > 0)
	      add(8*paramsOnStack, rsp)

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
    case LowIrReturn:
      if (stmt.tmpVar != null)
        movq(getTmp(stmt.tmpVar),rax)
      else
        movq(0,rax) //void fxns return 0
      leave()
      ret()
      break
    case LowIrCondJump:
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
      if(stmt.src.registerName != stmt.dst.registerName)
        movq(getTmp(stmt.src), getTmp(stmt.dst))
      break
    case LowIrBinOp:
      assert stmt.tmpVar instanceof RegisterTempVar;
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
        movq(getTmp(stmt.leftTmpVar),rax)
        movq(getTmp(stmt.rightTmpVar),r10)
        idiv(r10)
        movq(rax,getTmp(stmt.tmpVar))
        break
      case MOD:
        assert stmt.tmpVar.registerName == 'rdx'
        movq(0, rdx)
        movq(getTmp(stmt.leftTmpVar), rax)
        movq(getTmp(stmt.rightTmpVar), r10)
        idiv(r10)
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
      break;
    case LowIrLoadSpill:
      assert false;
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
