package decaf
import decaf.*
import static decaf.Type.*
import static decaf.BinOpType.*


class SemanticChecker {
  def errors
  def methodSymTable = [:];
  def hyperspeed = false

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

  def mainMethodCorrect(){
    def mainMethod = methodSymTable['main']
    // Check: main() method exists
    if(mainMethod){
      // Check: main() has return type of VOID
      if(mainMethod.returnType != Type.VOID){
        errors << new CompilerError(
          fileInfo: mainMethod.fileInfo,
          message: "Return type of main method should be void."
        )
      }
      // Check: main() has no arguments
      if(mainMethod.params.size() != 0){
        errors << new CompilerError(
          fileInfo: mainMethod.fileInfo,
          message: "main() should accept NO parameters."
        )
      }
      // Check: main() is the last function declared in the file
      def maximizeLineNumber = [compare:{a, b->
          def aLine = a?.fileInfo?.line
          def bLine = b?.fileInfo?.line
          (aLine == bLine) ? 0 : (aLine < bLine)? -1 : 1
      }] as Comparator

      def lastMethod = methodSymTable.values().max(maximizeLineNumber)
      if(lastMethod.name != "main"){
        errors << new CompilerError(
          fileInfo: mainMethod.fileInfo,
          message: "main() method is not the last method declared in the file."
        )
      }
    } else {
      errors << new CompilerError (
        fileInfo: null,
        message: "Function definition with prototype \"void main()\" does not exist (you didn't declare a main() method")
    }

  }

  def binOpOperands = { expr ->
    
    if(expr instanceof BinOp) {
      def leftType  = getExprType(expr.left);
      
      //Don't get the rightType of a NOT, since it'll be null and thus fail
      def rightType = expr.op != NOT ? getExprType(expr.right) : null;

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
        if (![INT, BOOLEAN].contains(leftType)) {
          errors << new CompilerError(
            fileInfo: expr.fileInfo,
            message: msg('integer or boolean','left')
          )
        }
        if(leftType != rightType) {
          errors << new CompilerError(
            fileInfo: expr.fileInfo,
            message: msg('the same type','each')
          )
        }
      } else if([AND, OR].contains(expr.op)) {
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
      } else if (expr.op == NOT) {
        if(leftType != BOOLEAN) {
          errors << new CompilerError(
            fileInfo: expr.fileInfo,
            message: msg('boolean','the')
          )
        }
      } else {
        assert false;
      }
    }
    if (!hyperspeed) {
      walk();
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
    if (!hyperspeed) {
      walk();
    }
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

    if (!hyperspeed) {
      walk();
      breakContinueForPost(cur)
    }
  }
  def breakContinueForPost(cur) {
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

    if (!hyperspeed) {
      walk();
    }
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

    if (!hyperspeed) {
      walk();
    }
  }
  
  // Actually the test below just enforces that all 
  // methods that don't have return type void do 
  // return a value (checks all paths through method).
  def nonVoidMethodsMustReturnValue = {cur ->
    try {
      declVar('methodDesc',null)
      declVar('returnCount', 0)
    } catch (MissingPropertyException e) {
      //Note: we don't throw the exception here b/c a += or -= will cause the location
      //and expression for the array index to be walked twice in 2 places, and we want
      //to avoid a garbage exception
      if (!(cur instanceof Expr)) {
        throw e
      }
    }

    if(cur instanceof Block && cur.parent == null) {
      // this is the top level block, check symbol table to extract 
      // the return type of the appropriate method declaration
      methodSymTable.keySet().each { it ->
        def desc = methodSymTable[it]
        if(desc.block == cur) {
          methodDesc = desc
        }
      }
    }
 
    if (!hyperspeed) {
      walk();
      nonVoidMethodsMustReturnValuePost(cur)
    }
  }

  def nonVoidMethodsMustReturnValuePost(cur) {
    if (cur.methodDesc?.returnType == VOID) return;
    switch (cur) {
    case Block:
      if(cur.returnCount != 0) {
        if (cur.parent != null)
          cur.parent.returnCount++
      } else {
        if (cur.parent == null) {
          //parent == null, top level block of method
          errors << new CompilerError(
            fileInfo: cur.fileInfo,
            message: "Missing return statement for method $cur.methodDesc"
          )
        }
      }
      break;
    case IfThenElse:
      if (cur.returnCount == 2)
        cur.parent.returnCount++
      break;
    case ForLoop:
      if(cur.returnCount != 0)
        cur.parent.returnCount++
      break;
    case Return:
      cur.parent.returnCount++
      break;
    }
  }

  def expectedReturnType = null;
  def methodDeclTypeMatchesTypeOfReturnExpr = {cur ->
    
    if(cur instanceof Block && cur.parent == null) {
      // this is the top level block, check symbol table to extract 
      // the return type of the appropriate method declaration
      methodSymTable.keySet().each { it ->
        if(methodSymTable[(it)].block.is(cur))
          expectedReturnType = methodSymTable[(it)].returnType;
      }
    }
    
    if(cur instanceof Return) {
      if(cur.expr == null) {
        if(expectedReturnType != VOID) {
          errors << new CompilerError(
            fileInfo: cur.fileInfo,
            message: "Type of Return expr (null) must match type of Method Declaration ($expectedReturnType)."
          )
        }
      } else {
        if(getExprType(cur.expr) != expectedReturnType) {
          errors << new CompilerError(
              fileInfo: cur.fileInfo,
              message: "Type of Return expr (${getExprType(cur.expr)}) must match type of Method Declaration ($expectedReturnType)."
            )
        }
      }
    }

    if (!hyperspeed) {
      walk();
    }
  }

  def arrayIndicesAreInts = { cur ->
    if (cur instanceof Location && cur.indexExpr != null) {
      if (![INT_ARRAY, BOOLEAN_ARRAY].contains(cur.descriptor.type)) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Encountered a scalar value ${cur.descriptor.name} being used as an array"
        )
      } else if (getExprType(cur.indexExpr) != INT) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Encountered array whose index is an ${getExprType(cur.indexExpr)}, expecting INT."
        )
      }
    }
    if (!hyperspeed) {
      walk();
    }
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
    if (!hyperspeed) {
      walk();
    }
  }

  def intLiteralsWithinRange = { cur ->
    if (cur instanceof IntLiteral) {
      if (cur.value > 2147483647 || cur.value < -2147483648) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Encountered int literal out of range: $cur.value Note that this value could be computed by statically computing a constant expression, so it might not actually be in your source code written as it was computed."
        )
      }
    }
    if (!hyperspeed) {
      walk();
    }
  }

  def dontShadowMethodParams = { cur ->
    if (cur instanceof Block && cur.parent == null) {
      cur.symTable.@map.each { k, v ->
        if (cur.symTable.parent.@map.containsKey(k)) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Local variable $k shouldn't shadow method parameter"
        )
        }
      }
    }

    if (!hyperspeed) {
      walk();
    }
  }

  def modifyDebugAssertCalls = { cur -> 
    if(methodSymTable.containsKey('assert')) {
      if(cur instanceof MethodCall) {
        if(cur.descriptor.name == 'assert') {
          cur.params << new IntLiteral(value: cur.fileInfo.line)
        }
      }

      if (!hyperspeed) {
        walk();
      }
    }
  }

  def hyperblast = {cur ->
    hyperspeed = true
    checks.each {
      if (it.is(arrayIndicesAreInts)) return
      it.delegate = delegate
      it(cur)
    }
    walk()
    breakContinueForPost(cur)
    //nonVoidMethodsMustReturnValuePost(cur)
  }

  //Put your checks here
  @Lazy def checks = {-> 
    [modifyDebugAssertCalls,
      breakContinueFor,
      assignmentTypesAreCorrect,
      methodCallArguments, 
      ifThenElseConditionCheck, 
      binOpOperands, 
      forLoopInitEndExprTypeInt,
      arrayIndicesAreInts,
      intLiteralsWithinRange,
      dontShadowMethodParams,
      //nonVoidMethodsMustReturnValue,
      methodDeclTypeMatchesTypeOfReturnExpr]}()
}
