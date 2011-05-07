package decaf.test
import decaf.*
import static decaf.BinOpType.*
import static decaf.graph.Traverser.eachNodeOf

class LCMTest extends GroovyTestCase {
  static int countLowir(GroovyMain gm, String methodName, Closure predicate) {
    return findLowir(gm, methodName, predicate).size()
  }
  static List findLowir(GroovyMain gm, String methodName, Closure predicate) {
    def s = []
    eachNodeOf(gm.ast.methodSymTable[methodName].lowir) { if (predicate(it)) { s << it } }
    return s
  }

  void testBinOpTotalRedundancy() {
    //move it to above the if
    def prog1 = '''
class Program {
  void foo(int a, int b) {
    int x, y, z;
    if (true) {
      x = a * b;
    } else {
      y = a * b;
    }
    callout("notdead",x,y,z,a,b);
  }
  void main() {
    foo(1,2);
  }
}
'''
    def gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
    assertEquals(2, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == MUL}))
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == MUL}))

    def prog2 = '''
class Program {
  void foo(int a, int b, int c) {
    int x, y;
    x = a * b * c;
    y = a * b * c;
    callout("notdead",x,y);
  }
  void main() {
    foo(1,2,3);
  }
}
'''
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': []])
    assertEquals(4, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == MUL}))
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': ['pre']])
    assertEquals(2, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == MUL}))

    def prog3 = '''
class Program {
  void foo(int a, int b) {
    int x, y;
    x = a * b;
    y = b * a;
    callout("notdead",x,y);
  }
  void main() {
    foo(1,2);
  }
}
'''
    gm = GroovyMain.runMain('genLowIr', prog3, ['opt': []])
    assertEquals(2, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == MUL}))
    gm = GroovyMain.runMain('genLowIr', prog3, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == MUL}))
  }

  void testBinOpPartialRedundancy() {
    //move it to above the if
    def prog2 = '''
class Program {
  void foo(int a, int b) {
    int x, y, z;
    if (true) {
      x = a * b;
    }
    y = a * b;
    callout("notdead",x,y,z,a,b);
  }
  void main() {
    foo(1,2);
  }							
}
'''
    def gm = GroovyMain.runMain('genLowIr', prog2, ['opt': []])
    assertEquals(2, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == MUL}))
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'foo', {it instanceof LowIrBinOp && it.op == MUL}))
  }

  void testLoadTotalRedundancy() {
    def prog1 = '''
class Program {
  int k;
  void main() {
    int x;
    k = 0;
    x = k;
    callout("notdead",x,k);
  }							
}
'''
    def gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
    assertEquals(2, countLowir(gm, 'main', {it instanceof LowIrLoad}))
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrStore}))
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrStore}))
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrLoad}))

    def prog2 = '''
class Program {
  int k;
  void main() {
    int x;
    x = k;
    x = k;
    callout("notdead",x,k);
  }							
}
'''
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': []])
    assertEquals(3, countLowir(gm, 'main', {it instanceof LowIrLoad}))
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrLoad}))
  }

  void testLoadPartialRedundancy() {
    def gm
    def prog1 = '''
class Program {
  int k;
  void main() {
    int x;
    if (true) {
      k = 0;
    }
    x = k;
  }				
}
'''
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrLoad}))
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrStore}))
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrStore}))
    assertEquals(0, countLowir(gm, 'main', {it instanceof LowIrLoad}))

    def prog2 = '''
class Program {
  int k;
  void main() {
    int x;
    if (true) {
      x = k;
    }
    x = k;
    callout("notdead",x,k);
  }							
}
'''
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': []])
    assertEquals(3, countLowir(gm, 'main', {it instanceof LowIrLoad}))
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrLoad}))
  }

  void testMethodClobbers() {
    def prog1 = '''
class Program {
  int k;
  void clobber() {
    k = 22;
  }
  void main() {
    int x;
    k = 0;
    clobber();
    x = k;
    callout("notdead",x,k);
  }							
}
'''
    def gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
    assertEquals(2, countLowir(gm, 'main', {it instanceof LowIrLoad}))
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrStore}))
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrStore}))
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrLoad}))
  }

  void testArrayTotalRedundancies() {
    def prog1 = '''
class Program {
  int a[10];
  void main() {
    int x, y;
    y = 0;
    x = a[y];
    x = a[y];
    callout("notdead",x,y);
  }							
}
'''
    def gm = GroovyMain.runMain('genLowIr', prog1, ['opt': []])
    assertEquals(2, countLowir(gm, 'main', {it instanceof LowIrLoad}))
    gm = GroovyMain.runMain('genLowIr', prog1, ['opt': ['pre']])
    assertEquals(1, countLowir(gm, 'main', {it instanceof LowIrLoad}))

    def prog2 = '''
class Program {
  int a[10];
  void main() {
    int x, y, z;
    y = 0;
    x = a[y];
    y = 1;
    z = a[y];
    callout("notdead",x,y,z);
  }							
}
'''
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': ['ssa']])
    assertEquals(2, countLowir(gm, 'main', {it instanceof LowIrLoad}))
    def loads = findLowir(gm,'main',{it instanceof LowIrLoad})
    assertTrue(loads[0].index != loads[1].index)
    gm = GroovyMain.runMain('genLowIr', prog2, ['opt': ['pre']])
    assertEquals(2, countLowir(gm, 'main', {it instanceof LowIrLoad}))
    loads = findLowir(gm,'main',{it instanceof LowIrLoad})
    assertTrue(loads[0].index != loads[1].index)
  }
}
