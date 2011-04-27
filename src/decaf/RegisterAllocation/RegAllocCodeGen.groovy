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
      movq(getTmp(stmt.condition), r11)
      cmp(1, r11)
      je(stmt.trueDest.label)
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

    if(stmt.anno["trace"]["FalseJmpSrc"]) {
      jmp(stmt.falseDest.label)
    } else if(stmt.anno["trace"]["JmpSrc"]) {
      jmp(stmt.anno["trace"]["JmpSrc"])
    }
  }
}
