package decaf.test
import decaf.*
import static decaf.DecafParserTokenTypes.*

class SymbolTableGeneratorTest extends GroovyTestCase {
  void testProgramOnly() {
    def y = new ASTBuilder()
    def x = y.build {
      Program(PROGRAM)
    }

    def symTableGenerator = new SymbolTableGenerator()
    x.inOrderWalk(symTableGenerator.c)
    def list = []
    x.symTable.inOrderWalk { list << it; walk()}
    x.methodSymTable.inOrderWalk { list << it; walk()}

    def testSymTable = new SymbolTable()
    def testMethodSymTable = new SymbolTable()
    def testList = []
    testSymTable.inOrderWalk { testList << it; walk()}
    testMethodSymTable.inOrderWalk { testList << it; walk()}

    assertEquals(testList.size(), list.size())
    assertEquals(testList.children.size(), list.children.size())
    for (int i = 0; i < testList.size(); i++) {
      assertEquals(testList[i].parent, list[i].parent)
    }
  }

  void testProgramAndVarDecl() {
    def y = new ASTBuilder()
    def x = y.build {
      Program(PROGRAM) {
        'int'(VAR_DECL) {
          a(ID)
          b(ARRAY_DECL) {
            '7'(INT_LITERAL)
          }
        }
      }
    }

    def symTableGenerator = new SymbolTableGenerator()
    x.inOrderWalk(symTableGenerator.c)
    def list = []
    x.symTable.inOrderWalk { list << it; walk()}
    x.methodSymTable.inOrderWalk { list << it; walk()}

    def testSymTable = new SymbolTable()
    testSymTable.walkerDelegate.declVar('vars',[])
    testSymTable.vars << new VariableDescriptor(name:'a',type:Type.INT)
    testSymTable.vars << new VariableDescriptor(name:'b',type:Type.INT_ARRAY)
    def testMethodSymTable = new SymbolTable()
    def testList = []
    testSymTable.inOrderWalk { testList << it; walk()}
    testMethodSymTable.inOrderWalk { testList << it; walk()}

    assertEquals(testList.size(), list.size())
    assertEquals(testList.children.size(), list.children.size())
    for (int i = 0; i < testList.size(); i++) {
      assertEquals(testList[i].parent, list[i].parent)
    }
  }   
}
