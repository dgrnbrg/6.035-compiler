package decaf.test
import decaf.*

class TempVarTest extends GroovyTestCase {

  void testTempAllocOne(){
    HiIrBuilder builder = new HiIrBuilder()
    def errors = []
    Block twoTempVarsNeeded = builder.Block(){
      method(name:"assignConstant", returns:Type.VOID)
      var(name:'a', type:Type.INT)
      Assignment(line: 0) { Location('a'); lit(3) }
    }
    //println twoTempVarsNeeded
    builder.methodSymTable['assignConstant'].block = twoTempVarsNeeded
    builder.methodSymTable['assignConstant'].tempFactory.decorateMethodDesc()
    assertEquals(3, builder.methodSymTable['assignConstant'].tempFactory.tmpVarId)
  }
}
