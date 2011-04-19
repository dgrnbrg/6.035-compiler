package decaf.test
import decaf.*
import static decaf.BinOpType.*
import static decaf.graph.Traverser.eachNodeOf

class SCCPLowIrTest extends GroovyTestCase {
  static int countLowir(GroovyMain gm, String methodName, Closure predicate) {
    return findLowir(gm, methodName, predicate).size()
  }
  static List findLowir(GroovyMain gm, String methodName, Closure predicate) {
    def s = []
    eachNodeOf(gm.ast.methodSymTable[methodName].lowir) { if (predicate(it)) { s << it } }
    return s
  }

  void testConditionalElimination() {
    //remove the false branch since it's never taken
    def prog1 = '''
class Program {
  void foo() {
    int a;
    int b;
    b = 2;
    a = 3;
    a = a + b;
    if (a == 5) {
      b = a + b;
    } else {
      b = b + a;
    }
    callout("printf", "b is %d", b);
  }
  void main() {
    foo();
  }
}
'''
    def gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
    assertEquals(3, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == ADD}))
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrCondJump}))
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['sccp']])
    assertEquals(0, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == ADD}))
    assertEquals(0, countLowir(gm, 'foo', {it instanceof LowIrCondJump}))

    def prog2 = '''
class Program {
  void bar() {
    int a;
    int b;
    a = 1;
    b = 0;
    a = a + b;
    for i=1, 10 {
      if (i > 5) {
        a = a + i;
      } else {
        b = b + i;
      }
    }
    callout("printf", "a is %d\\n", a);
    callout("printf", "b is %d\\n", b);
  }
  void main() {
    bar();
  }
}
'''
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': []])
    assertEquals(4, countLowir(gm, 'bar', {it instanceof LowIrBinOp && it.op == ADD}))
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': ['sccp']])
    assertEquals(3, countLowir(gm, 'bar', {it instanceof LowIrBinOp && it.op == ADD}))
  }

  void testIdentity() {
    // test all arithmetic identity operations
    def prog = '''
class Program{
  int mul0(int x) {
    return x * 0;
  }
  int mul1(int x) {
    return 1 * x;
  }
  int add0(int x) {
    return 0 + x;
  }
  int sub0(int x) {
    return x - 0;
  }
  int div0(int x) {
    return 0 / x;
  }
  int mod0(int x) {
    return 0 % x;
  }
  void main() {
  }
}'''
    def gm = GroovyMain.runMain('genLowIr', prog, ['opt':['sccp']])
    gm.ast.methodSymTable.keySet().findAll{ it != 'main' }.each{
      assertEquals(0, countLowir(gm, it, {it instanceof LowIrBinOp}));
    }
  }
}
