package decaf

import static decaf.Type.*
import static decaf.BinOpType.*


class SemanticChecker {
  def errors

  // Need method to access symbol table
  // Input: an expression
  // Output: the type that it should evaluate to
  // If the expression is found to be ill-typed, then we throw an exception

  Closure getExprType = {expr -> 

    if(expr instanceof Location) {
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
    } else if(expr instanceof BinOp) {
      def leftType  = getExprType(expr.left);
      def rightType = getExprType(expr.right);
      
      if([ADD, SUB, MUL, DIV, MOD, LT, GT, LTE, GTE].contains(expr.op)) {
        if(leftType == INT && rightType == INT) {
          if([ADD, SUB, MUL, DIV, MOD].contains(expr.op)) {
            return INT;
          } else { 
            // must be in [LT, GT, LTE, GTE]
            return BOOLEAN;
          }
          return INT; 
        } else {
          if(leftType != INT) {
            errors << new CompilerError(
              fileInfo: expr.fileInfo,
              message: "Encountered binary operator: ${expr.op}, expecting left operand to be an integer. "
            ) 
          } 

          if(rightType != INT) {
            errors << new CompilerError(
              fileInfo: expr.fileInfo,
              message: "Encountered binary operator expecting right operand to be an integer(array)."
            ) 
          }
        }
      } else if([EQ, NEQ].contains(expr.op)) {
        // maybe someone can verify that I'm not making a mistake 
        // for not explicitly checking for boolean arrays and int arrays?
        if(leftType == rightType) {
          return BOOLEAN;
        } else {
          errors << new CompilerError(
              fileInfo: expr.fileInfo,
              message: "Encountered binary operator expecting right operand and left operand to have same type."
            )
        }
      } else if([AND, OR, NOT].contains(expr.op)) {
        if(leftType == BOOLEAN && rightType == BOOLEAN) {
          return BOOLEAN;
        } else {
          if(leftType != BOOLEAN) {
            errors << new CompilerError(
              fileInfo: expr.fileInfo,
              message: "Encountered binary operator expecting left operand to have type boolean."
            )
          }

          if(rightType != BOOLEAN) {
            errors << new CompilerError(
              fileInfo: expr.fileInfo,
              message: "Encountered binary operator expecting right operand to have type boolean."
            )
          }
        }
      } else {
        // should never reach this state
        assert(false);
      }
    } else if(expr instanceof BooleanLiteral) {
      return BOOLEAN;
    } else if(expr instanceof IntLiteral) {
      return INT;
    } else if(expr instanceof CallOut) {
      return INT;
    } else if(expr instanceof MethodCall) {
      return expr.descriptor.returnType;
    } else {
      assert(false);
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

  //Put your checks here
  @Lazy def checks = {->[breakContinueFor, methodCallArguments, ifThenElseConditionCheck]}()
}
