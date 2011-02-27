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
    walk()
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
    walk()
  }

  def arrayIndicesAreInts = { cur ->
    if (cur instanceof Location && cur.indexExpr != null) {
      if (getExprType(cur.indexExpr) != INT) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Encountered array whose index is an ${getExprType(cur.indexExpr)}, expecting INT."
        )
      }
    }
    walk()
  }

  def assignmentTypesAreCorrect = { cur ->
    if (cur instanceof Assignment) {
      def lhs = getExprType(cur.loc)
      def rhs = getExprType(cur.expr)
      if (![INT, BOOLEAN].contains(lhs)) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Encountered assignment to a non-scalar type"
        )
      } else if (lhs != rhs) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Encountered assignment with mismatched types. Left hand side was $lhs, right hand side was $rhs"
        )
      }
    }
    walk()
  }
  
  //Put your checks here
  @Lazy def checks = {-> 
    [breakContinueFor,
      assignmentTypesAreCorrect,
      methodCallArguments, 
      ifThenElseConditionCheck, 
      binOpOperands, 
      forLoopInitEndExprTypeInt,
      arrayIndicesAreInts]}()
}
