package decaf.test
import decaf.*
import static decaf.graph.Traverser.eachNodeOf

class DeadStoreEliminationTest extends GroovyTestCase {
  static int countLowir(GroovyMain gm, String methodName, Closure predicate) {
    return findLowir(gm, methodName, predicate).size()
  }
  static List findLowir(GroovyMain gm, String methodName, Closure predicate) {
    def s = []
    eachNodeOf(gm.ast.methodSymTable[methodName].lowir) { if (predicate(it)) { s << it } }
    return s
  }

  void testDeadStoreEliminatoin() {
    def prog1 = '''
class Program {
  int a;
  int b;
  int foo() {
    a = 3;
    a = 4;
    return 0;
  }
  void main() {
  }
}
'''
    def gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
    assertEquals(2, countLowir(gm, 'foo', {it instanceof LowIrStore}))
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['cp','cse','dce','dse']])
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrStore}))
  }

  void testDeadStoreElimConditional() {
    def prog1 = '''
class Program {
  int a;
  int b;
  int foo() {
    a = 3;
    a = 4;
    for i=0,10{
      if (i > 5) {
        a = 22;
      } else {
      }
    }
    return 0;
  }
  void main() {
  }
}
'''
    def gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
    assertEquals(3, countLowir(gm, 'foo', {it instanceof LowIrStore}))
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['cp','cse','dce','dse']])
    assertEquals(2, countLowir(gm, 'foo', {it instanceof LowIrStore}))

    def prog2 = '''
class Program {
  int a;
  int b;
  int foo() {
    a = 3;
    a = 4;
    for i=0,10{
      if (i > 5) {
        a = 22;
      } else {
        a = 22;
      }
    }
    return 0;
  }
  void main() {
  }
}
'''
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': []])
    assertEquals(4, countLowir(gm, 'foo', {it instanceof LowIrStore}))
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': ['cp','cse','dce','sccp','dse']])
    assertEquals(2, countLowir(gm, 'foo', {it instanceof LowIrStore}))
  }
}
