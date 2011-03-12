package decaf.test
import decaf.*
import static decaf.BinOpType.*
import static decaf.Type.*
import static decaf.DecafParserTokenTypes.*

class HiIrGeneratorTest extends GroovyTestCase {
  void testDecimalLiterals() {
    def astbuilder = new ASTBuilder()
    def intliteralhiir = astbuilder.compile {
      Program(PROGRAM) {
        main(METHOD_DECL) {
          'void'(TK_void)
          block(BLOCK) {
            'int'(VAR_DECL) {a(ID)}
            //small int
            'assignment'(STATEMENT) {
              loc(LOCATION) {a(ID)}
              '='(PLAIN_ASSIGN_OP)
              '10'(INT_LITERAL)
            }
            //bit 31 set, all others cleared
            'assignment'(STATEMENT) {
              loc(LOCATION) {a(ID)}
              '='(PLAIN_ASSIGN_OP)
              '2147483648'(INT_LITERAL)
            }
          }
        }
      }
    }
    assertTrue(intliteralhiir instanceof HiIrGenerator)
    assertEquals(0, intliteralhiir.errors.size())
    def toFind = [2147483648L, 10L]
    intliteralhiir.methods['main'].inOrderWalk { cur ->
      if (cur instanceof IntLiteral) {
        assertTrue(toFind.remove((Object)cur.value))
      }
      walk()
    }
    assertEquals(0, toFind.size())
  }

  void testHexLiterals() {
    def astbuilder = new ASTBuilder()
    def intliteralhiir = astbuilder.compile {
      Program(PROGRAM) {
        main(METHOD_DECL) {
          'void'(TK_void)
          block(BLOCK) {
            'int'(VAR_DECL) {a(ID)}
            //mixed cases
            'assignment'(STATEMENT) {
              loc(LOCATION) {a(ID)}
              '='(PLAIN_ASSIGN_OP)
              '0x5afE'(INT_LITERAL)
            }
            //lowercase
            'assignment'(STATEMENT) {
              loc(LOCATION) {a(ID)}
              '='(PLAIN_ASSIGN_OP)
              '0x7ffff0'(INT_LITERAL)
            }
            //uppercase
            'assignment'(STATEMENT) {
              loc(LOCATION) {a(ID)}
              '='(PLAIN_ASSIGN_OP)
              '0xCAFE'(INT_LITERAL)
            }
          }
        }
      }
    }
    assertTrue(intliteralhiir instanceof HiIrGenerator)
    assertEquals(0, intliteralhiir.errors.size())
    def toFind = [0x5afeL, 0x7ffff0L, 0xcafeL]
    intliteralhiir.methods['main'].inOrderWalk { cur ->
      if (cur instanceof IntLiteral) {
        assertTrue(toFind.remove((Object)cur.value))
      }
      walk()
    }
    assertEquals(0, toFind.size())
  }

  void testConstantFolding() {
    def gm = GroovyMain.runMain('genHiIr', '''
class Program {
  void main() {
    int a;
    boolean b;
    a = 1 + 1;
    a = 2 - 1;
    a = 2 * 2;
    a = 6 / 2;
    a = 11 % 6;
    b = 1 < 2;
    b = false == true;
  }
}
''')
    assertEquals(0, gm.errors.size())
    def toFind = [2L, 1L, 4L, 3L, 5L, false, true]
    gm.hiirGenerator.methods['main'].inOrderWalk { cur ->
      if (cur instanceof IntLiteral || cur instanceof BooleanLiteral) {
        assertTrue(toFind.remove((Object)cur.value))
      }
      walk()
    }
    assertEquals(0, toFind.size())
  }
}
