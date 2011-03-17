package decaf.test
import decaf.*
import static decaf.BinOpType.*

class LowIrGeneratorTest extends GroovyTestCase {
  void testAssignment() {
    def gen = new LowIrGenerator()
    def hb = new HiIrBuilder()
    def assignment = hb.Block{
      method(name: 'foo', returns: Type.VOID)
      var(name:'a', type:Type.INT)
      Assignment {
        Location('a')
        BinOp(ADD) {
          lit(3)
          BinOp(MUL) {
            lit(4)
            lit(1)
          }
        }
      }
    }
    def methodDesc = hb.methodSymTable['foo']
    methodDesc.block = assignment
    methodDesc.tempFactory.decorateMethodDesc()
    gen.destruct(methodDesc)
  }
  void testIfElse() {
    def gen = new LowIrGenerator()
    def hb = new HiIrBuilder()
    def if1 = hb.Block{
      method(name: 'foo', returns: Type.VOID)
      var(name:'a', type:Type.INT)
      IfThenElse {
        lit(true)
        Block {
          Assignment{ Location('a'); lit(2) }
        }
        Block {
          Assignment{ Location('a'); lit(4) }
        }
      }
    }
    def methodDesc = hb.methodSymTable['foo']
    methodDesc.block = if1
    methodDesc.tempFactory.decorateMethodDesc()
    gen.destruct(methodDesc)
  }
}
