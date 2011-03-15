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
    def semCheck = new SemanticChecker()
    //semantic checker does the tempvar computations
    assignment.inOrderWalk(semCheck.hyperblast)
    gen.destruct(assignment)
  }

  // Nathan: Re-enable this!
  // void testIfElse() {
  //   def gen = new LowIrGenerator()
  //   def hb = new HiIrBuilder()
  //   def if1 = hb.Block{
  //     var(name:'a', type:Type.INT)
  //     IfThenElse {
  //       lit(true)
  //       Block {
  //         Assignment{ Location('a'); lit(2) }
  //       }
  //       Block {
  //         Assignment{ Location('a'); lit(4) }
  //       }
  //     }
  //   }
  //   def semCheck = new SemanticChecker()
  //   if1.inOrderWalk(semCheck.hyperblast)
  //   gen.destruct(if1)
  // }

  void testIfElse1() {
    // No Else block
    def gen = new LowIrGenerator()
    def hb = new HiIrBuilder()
    def if1 = hb.Block{
      var(name:'a', type:Type.INT)
      IfThenElse {
        lit(true)
        Block {
          Assignment { Location('a'); lit(2)}
        }
      }
    }
    def semCheck = new SemanticChecker()
    if1.inOrderWalk(semCheck.hyperblast)
    gen.destruct(if1)
  }
}
