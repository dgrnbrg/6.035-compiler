package decaf.test
import decaf.*
import static decaf.DecafParserTokenTypes.*

class SymbolTableGeneratorTest extends GroovyTestCase {
 
  void testProgramAndVarDeclAndMethodDecl() {
    def y = new ASTBuilder()
    def x = y.build {
      Program(PROGRAM) {
        'int'(VAR_DECL) {
          a(ID)
          b(ARRAY_DECL) {
            '7'(INT_LITERAL)
          }
        }
        'boolean'(VAR_DECL) {
          c(ID)
        }
        foo(METHOD_DECL) {
          'int'(TK_int)
          'int'(VAR_DECL) {
            a(ID)
          }
          block(BLOCK)
      }
    }
  }

    def errors = []
    def symTableGenerator = new SymbolTableGenerator(errors:errors)
    x.inOrderWalk(symTableGenerator.c)
    def list = []
    x.symTable.inOrderWalk { list << it; walk()}
    x.methodSymTable.inOrderWalk { list << it; walk()}

    def testSymTable = new SymbolTable()
    testSymTable.walkerDelegate.declVar('vars',[])
    def a = new VariableDescriptor(name:'a',type:Type.INT)
    def b = new VariableDescriptor(name:'b',type:Type.INT_ARRAY,arraySize:7)
    def c = new VariableDescriptor(name:'c',type:Type.BOOLEAN)
    testSymTable['a'] = a
    testSymTable['b'] = b
    testSymTable['c'] = c 

    def testFooSymTable = new SymbolTable(testSymTable)
    testFooSymTable.walkerDelegate.declVar('vars',[])
    testFooSymTable['a'] = new VariableDescriptor(name:'a',type:Type.INT)

    def testFooBlockSymTable = new SymbolTable(testFooSymTable)

    def testMethodSymTable = new SymbolTable()
    def param = []
    param << new VariableDescriptor(name:'a',type:Type.INT)
    testMethodSymTable.walkerDelegate.declVar('methodDesc', new MethodDescriptor(name:'foo',returnType:Type.INT,params:param))
    testMethodSymTable['foo'] = new MethodDescriptor(name:'foo',returnType:Type.INT,params:param)

    def testList = []
    testSymTable.inOrderWalk { testList << it; walk()}
    testMethodSymTable.inOrderWalk { testList << it; walk()}

    assertEquals(testList.size(), list.size())
    assertEquals(testList.children.size(), list.children.size())

    assertEquals(testList, list)
  }

  void testProgram2() {
    def y = new ASTBuilder()
    def x = y.build {
      Program(PROGRAM) {
        'int'(VAR_DECL) {
          a(ID)
          b(ARRAY_DECL) {
            '7'(INT_LITERAL)
          }
        }
        'boolean'(VAR_DECL) {
          c(ID)
          d(ARRAY_DECL) {
            '2'(INT_LITERAL)
          }
        }
        foo(METHOD_DECL) {
          'int'(TK_int)
          'int'(VAR_DECL) {
            bb(ID)
          }
          'int'(VAR_DECL) {
            aa(ID)
          }
          block(BLOCK) {
            'int'(VAR_DECL) {
              i(ID)
            }
            'int'(VAR_DECL) {
              k(ID)
            }
            'for'(TK_for) {
              a(ID)
              '0'(INT_LITERAL)
              '10'(INT_LITERAL)
              block(BLOCK) {
                'int'(VAR_DECL) {
                  a(ID)
                }
              }
            }
            block2(BLOCK) {
              'boolean'(VAR_DECL) {
                b(ID)
              }
            }
          }
      }
    }
  }

    def errors = []
    def symTableGenerator = new SymbolTableGenerator(errors:errors)
    x.inOrderWalk(symTableGenerator.c)
    def list = []
    x.symTable.inOrderWalk { list << it; walk()}
    x.methodSymTable.inOrderWalk { list << it; walk()}

    def testSymTable = new SymbolTable()
    testSymTable.walkerDelegate.declVar('vars',[])
    def a = new VariableDescriptor(name:'a',type:Type.INT)
    def b = new VariableDescriptor(name:'b',type:Type.INT_ARRAY,arraySize:7)
    def c = new VariableDescriptor(name:'c',type:Type.BOOLEAN)
    def d = new VariableDescriptor(name:'d',type:Type.BOOLEAN_ARRAY,arraySize:2)
    testSymTable['a'] = a
    testSymTable['b'] = b
    testSymTable['c'] = c 
    testSymTable['d'] = d

    def testFooSymTable = new SymbolTable(testSymTable)
    testFooSymTable.walkerDelegate.declVar('vars',[])
    testFooSymTable['bb'] = new VariableDescriptor(name:'bb',type:Type.INT)
    testFooSymTable['aa'] = new VariableDescriptor(name:'aa',type:Type.INT)

    def testFooBlockSymTable = new SymbolTable(testFooSymTable)
    testFooBlockSymTable.walkerDelegate.declVar('vars',[])
    testFooBlockSymTable['i'] = new VariableDescriptor(name:'i',type:Type.INT)
    testFooBlockSymTable['k'] = new VariableDescriptor(name:'k',type:Type.INT)
    
    def testBlock2SymTable = new SymbolTable(testFooBlockSymTable)
    testBlock2SymTable.walkerDelegate.declVar('vars',[])
    testBlock2SymTable['a'] = new VariableDescriptor(name:'a',type:Type.INT)

    def testBlock3SymTable = new SymbolTable(testBlock2SymTable)
    testBlock3SymTable.walkerDelegate.declVar('vars',[])
    testBlock3SymTable['a'] = new VariableDescriptor(name:'a',type:Type.INT)

    def testBlock1SymTable = new SymbolTable(testFooBlockSymTable)
    testBlock1SymTable.walkerDelegate.declVar('vars',[])
    testBlock1SymTable['b'] = new VariableDescriptor(name:'b',type:Type.BOOLEAN)

    def testMethodSymTable = new SymbolTable()
    def param = []
    param << new VariableDescriptor(name:'bb',type:Type.INT)
    param << new VariableDescriptor(name:'aa',type:Type.INT)
    testMethodSymTable.walkerDelegate.declVar('methodDesc', new MethodDescriptor(name:'foo',returnType:Type.INT,params:param))
    testMethodSymTable['foo'] = new MethodDescriptor(name:'foo',returnType:Type.INT,params:param)

    def testList = []
    testSymTable.inOrderWalk { testList << it; walk()}
    testMethodSymTable.inOrderWalk { testList << it; walk()}

    assertEquals(testList.size(), list.size())
    assertEquals(testList.children.size(), list.children.size())

    assertEquals(testList, list)
  }   
}
