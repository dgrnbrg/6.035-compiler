package decaf

class SemanticChecker {
  def errors

  // Need method to access symbol table
  // Input: an expression
  // Output: the type that it should evaluate to
  // If the expression is found to be ill-typed, then we throw an exception
  Closure getExprType = {expression->
    if(expression instanceof BinOp){
      switch(expression.op){
        case BinOpType.ADD:
          walk()
          if(expression.left.operandType == Type.INT && expression.right.operandType == Type.INT){
            declVar('operandType', Type.INT)
          } else {
            throw new RuntimeException("[types incorrect] BinOp(+)!")
          }
      }
    }
    if(expression instanceof IntLiteral){
      declVar('operandType', Type.INT)
    }
    else if(expression instanceof BooleanLiteral){
      declVar('operandType', Type.BOOLEAN)
    }
  }
   
  def methodCallArguments = {current ->
    if(current instanceof MethodCall){
      println current
      println "printing descriptor!"
      println current.descriptor
      println "printing parameters"
      println current.params
      [current.descriptor.params, current.params].transpose().each{ a,b ->
        println a
        //println b.type
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

  //Put your checks here
  @Lazy def checks = {->[breakContinueFor,methodCallArguments]}()
}
