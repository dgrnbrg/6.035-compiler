package decaf.test
import decaf.*
import static decaf.BinOpType.*

class LowIrGeneratorTest extends GroovyTestCase {
  void testAssignment() {
    def gen = new LowIrGenerator()
    def hb = new HiIrBuilder()
    def assignment = hb.Block{
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
    gen.destruct(assignment)
  }
  void testIfElse() {
    def gen = new LowIrGenerator()
    def hb = new HiIrBuilder()
    def if1 = hb.Block{
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
    gen.destruct(if1)
  }
}
