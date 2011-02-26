package decaf

import static decaf.Type.*
import static decaf.BinOpType.*


class SemanticChecker {
  def errors

  static Type getExprType(Expr expr) {
    switch (expr) {
    case Location:
      def returnType = expr.descriptor.type;
      
      if(expr.indexExpr == null) {
        return returnType;
      } else {
        if(returnType == INT_ARRAY) {
          return INT;
        } else if(returnType == BOOLEAN_ARRAY) {
          return BOOLEAN;
        } else {
          throw new RuntimeException("Should run array semantic check first.");
        }
      }
    case BinOp:
      switch (expr.op) {
      case [ADD, SUB, MUL, DIV, MOD]:
        return INT;
      case [LT, GT, LTE, GTE, EQ, NEQ, AND, OR, NOT]:
        return BOOLEAN;
      default:
        // should never reach this state
        assert(false);
      }
    case BooleanLiteral:
      return BOOLEAN;
    case IntLiteral:
    case CallOut:
      return INT;
    case MethodCall:
      return expr.descriptor.returnType;
    default:
      assert false;
    }
  }

  def binOpOperands = { expr ->
    if(expr instanceof BinOp) {
      def leftType  = getExprType(expr.left);
      def rightType = getExprType(expr.right);
      def msg = {type, side -> "Encountered binary operator ${expr.op}, expecting $side operand to be $type"} 
      if([ADD, SUB, MUL, DIV, MOD, LT, GT, LTE, GTE].contains(expr.op)) {
        if(leftType != INT) {
          errors << new CompilerError(
            fileInfo: expr.fileInfo,
            message: msg('integer','left')
          ) 
        } 

        if(rightType != INT) {
          errors << new CompilerError(
            fileInfo: expr.fileInfo,
            message: msg('integer','right')
          ) 
        }
      } else if([EQ, NEQ].contains(expr.op)) {
        // maybe someone can verify that I'm not making a mistake 
        // for not explicitly checking for boolean arrays and int arrays?
        if(leftType != rightType) {
          errors << new CompilerError(
            fileInfo: expr.fileInfo,
            message: msg('the same type','each')
          )
        }
      } else if([AND, OR, NOT].contains(expr.op)) {
        if(leftType != BOOLEAN) {
          errors << new CompilerError(
            fileInfo: expr.fileInfo,
            message: msg('boolean','left')
          )
        }

        if(rightType != BOOLEAN) {
          errors << new CompilerError(
            fileInfo: expr.fileInfo,
            message: msg('boolean','right')
          )
        }
      } else {
        assert false;
      }
    }
  }

  def methodCallArguments = {current ->
    if(current instanceof MethodCall){
      def typeList = current.descriptor.params
      def argList = current.params
      // Ensure that the caller actually passed the correct number of parameters
      if(typeList.size() != argList.size()){
        errors << new CompilerError(
          fileInfo: current.fileInfo,
          message: "Encountered method call to ${current.descriptor.name} with different number of parameters than specified in function prototype"
        )
      }
      // Ensure that the caller passed parameters of the correct type
      [typeList,argList].transpose().each{ argDescriptor, argValue->
        def argValueType = getExprType(argValue)
        def argType = argDescriptor.type
        if(argValueType != argType){
          errors << new CompilerError(
            fileInfo: current.fileInfo,
            message: "Encountered method call to ${current.descriptor.name} with parameter of type ${argValueType}, which should be of type ${argType} instead."
          )
        }
      }
    }
    walk()
  }
  
  int nestedForDepth = 0
  def breakContinueFor = {cur ->
    if (cur instanceof ForLoop) {
      nestedForDepth++
    } else if (cur instanceof Break && nestedForDepth == 0) {
      errors << new CompilerError(
        fileInfo: cur.fileInfo,
        message: "Encountered break outside of for loop"
      ) 
    } else if (cur instanceof Continue && nestedForDepth == 0) {
      errors << new CompilerError(
        fileInfo: cur.fileInfo,
        message: "Encountered continue outside of for loop"
      )
    }

    walk()

    if (cur instanceof ForLoop) {
      nestedForDepth--
    }
  }

  def ifThenElseConditionCheck = {cur -> 
    if(cur instanceof IfThenElse) {
      if(getExprType(cur.condition) != BOOLEAN) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Encountered if statement, expected boolean condition."
        )
      }
    }

    walk();
  }

  def forLoopInitEndExprTypeInt = {cur -> 
    if(cur instanceof ForLoop) {
      if(getExprType(cur.low) != INT) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo, 
          message: "Encountered ForLoop, expected init expression to be of type INT."
        )
      }

      if(getExprType(cur.high) != INT) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Encountered ForLoop, expected end expression to be of type INT."
        )
      }
    }

    walk();
  }
  
  def methodCallsThatAreExprReturnValue = {cur -> 
    if (cur instanceof Statement) {
      declVar('returnCount', 0)
    }

    walk();

    switch (cur) {
    case Block:
      if(returnCount != 0) {
        if (parent != null) {
          parent.returnCount++
        } else if (returnCount == 0) {
          //parent == null, top level block of method
          errors << new CompilerError(
            fileInfo: fileInfo,
            message: "Missing return statement in method"
          )
        }
      }
      break;
    case IfThenElse:
      if (returnCount == 2)
        parent.returnCount++
      break;
    case ForLoop:
      if(returnCount != 0) 
        parents.returnCount++
      break;
    case Return:
      parent.returnCount++
      break;
    }
  }

  def expectedReturnType = null;
  def methodDeclTypeMatchesTypeOfReturnExpr = {cur ->
    if(cur instanceof Block && cur.parent == null) {
      // this is the top level block, check symbol table to extract 
      // the return type of the appropriate method declaration
      assert(expectedReturnType);
      correctMethodDesc = cur.methodSymTable.values().findAll { -> 
        it.block.is(cur)
      }
      
      // we should have only found one method that matches this block
      assert(correctMethodDesc.size() == 1);
      expectedReturnType = correctMethodDesc[0].returnType;
    } else {
      declVar('expectedReturnType', parent.expectedReturnType)
    }
    
    if(cur instanceof Return) {
      if(getExprType(cur.expr) != expectedReturnType) {
        errors << new CompilerError(
            fileInfo: fileInfo,
            message: "Type of Return expr must match type of Method Declaration."
          )
      }
    }

    walk();
  }
  
  //Put your checks here
  @Lazy def checks = {-> 
    [breakContinueFor, 
      methodCallArguments, 
      ifThenElseConditionCheck, 
      binOpOperands, 
      forLoopInitEndExprTypeInt]}()
}
