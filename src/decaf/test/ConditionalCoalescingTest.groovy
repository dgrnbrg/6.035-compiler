package decaf.test
import decaf.*
import static decaf.BinOpType.*
import static decaf.graph.Traverser.eachNodeOf

class ConditionalCoalescingTest extends GroovyTestCase {
  static int countLowir(GroovyMain gm, String methodName, Closure predicate) {
    return findLowir(gm, methodName, predicate).size()
  }
  static List findLowir(GroovyMain gm, String methodName, Closure predicate) {
    def s = []
    eachNodeOf(gm.ast.methodSymTable[methodName].lowir) { if (predicate(it)) { s << it } }
    return s
  }

  void testConditionalCoalescing() {
    def prog1 = '''
class Program {
  boolean foo() {
    int a;
    int b;
    boolean c;
    int d;
    d = 1;
    a = 1;
    b = 2;
    c = a < b;
    if (c) {
      callout("printf", "hello world\\n");
    }
    for i=0,10 {
      c = i < a;
      if (c) {
      }
      if (i <= a) {
        callout("printf", "hello world\\n");
      }
      if (i == a) {
        callout("printf", "hello world\\n");
      }
      if (i >= a) {
        callout("printf", "hello world\\n");
      }
      if (i > a) {
        callout("printf", "hello world\\n");
      }
      if (i != a) {
        callout("printf", "hello world\\n");
      }
    }
    return c;
  }
  void main() {
    foo();
  }
}
'''

    def gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
//println "errors are ${gm.failException}"
    assertEquals(9, countLowir(gm, 'foo', {it instanceof LowIrCondJump}))
    assertEquals(4, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == LT}))
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == LTE}))
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == EQ}))
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == GTE}))
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == GT}))
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == NEQ}))

    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['all']])
    assertEquals(0, countLowir(gm, 'foo', {it instanceof LowIrCondJump && ! it instanceof LowIrCondCoalesced}))
    assertEquals(7, countLowir(gm, 'foo', {it instanceof LowIrCondCoalesced}))
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == LT}))
    assertEquals(0, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == LTE}))
    assertEquals(0, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == EQ}))
    assertEquals(0, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == GTE}))
    assertEquals(0, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == GT}))
    assertEquals(0, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == NEQ}))
  }
}
