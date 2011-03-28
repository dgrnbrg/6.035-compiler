package decaf
import decaf.*
import decaf.test.*
import static decaf.Type.*

class AssertFn {

  def static AssertFunctionEnabled = false
  def static MethodDescriptor getAssertMethodDesc() {

    FileInfo nullFI = new FileInfo(line: -1, col: -1)

    VariableDescriptor varExprToAssert = new VariableDescriptor(
      name: "argExprToAssert",
      type: Type.BOOLEAN,
      fileInfo: nullFI
    )

    VariableDescriptor varLineNum = new VariableDescriptor(
      name: "argLineNum",
      type: Type.INT,
      fileInfo: nullFI
    )

    HiIrBuilder hb = new HiIrBuilder()

    def assertBlock = hb.Block {
      IfThenElse() {
        lit(true)
        Block() { 
          Return()
        }
        Block() { 
          CallOut("printf") {
            lit("DECAF ASSERT FAILED on LINE NUMBER: %d\\n")
            lit(2)
          }
          CallOut("exit") {
            lit(-1)
          }
        }
      }
    }

    // Now go in and replace thigns in the assertBlock
    assert(assertBlock.statements.size() == 1)
    assert(assertBlock.statements.first() instanceof IfThenElse)

    Location locExprToAssert = new Location(descriptor: varExprToAssert);
    locExprToAssert.fileInfo = nullFI
    assertBlock.statements.first().condition = locExprToAssert

    assert(assertBlock.statements.first().elseBlock.statements.first() instanceof CallOut)

    // THE FOLLOWING LINE IS A HACK AND NEEDS TO BE FIXED after consultation. (find sagar) 
    // Suspected cause: I think there was an error propagated during codegen phase of project in 
    // HiIr builder - specifically, name is not being correctly set as a StringLiteral (perhaps directly 
    // being set as a string value.
    assertBlock.statements.first().elseBlock.statements.first().name = new StringLiteral(value: "printf")

    Location locLineNum = new Location(descriptor: varLineNum)
    locLineNum.fileInfo = nullFI
    assertBlock.statements.first().elseBlock.statements.first().params[1] = locLineNum

    // This line is also a hack to get the exit function working.
    assertBlock.statements.first().elseBlock.statements[1].name = new StringLiteral(value: "exit")
    IntLiteral negOne = new IntLiteral(value: 1)
    negOne.fileInfo = nullFI
    assertBlock.statements.first().elseBlock.statements[1].params[0] = negOne

    def assertMD = new MethodDescriptor( 
      name: "assert",
      returnType: Type.VOID,
      block: assertBlock,
      params: [varExprToAssert, varLineNum],
      fileInfo: nullFI
    )

    return assertMD
  }

}
