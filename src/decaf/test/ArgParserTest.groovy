package decaf.test
import decaf.ArgParser

class ArgParserTest extends GroovyTestCase {
  def parser

  void checkMap(Map a, Map b) {
    assertEquals(a.size(), b.size())
    a.each{ k, v ->
      assertTrue(b.containsKey(k))
      assertEquals(v, b[k])
    }
  }

  void testValid() {
    parser = new ArgParser()
    parser.parse([])
    checkMap([other:[]], parser.results)

    parser = new ArgParser()
    parser.parse(['-debug'])
    checkMap([other:[], debug:[]], parser.results)

    parser = new ArgParser()
    parser.parse(['fark'])
    checkMap([other:['fark']], parser.results)

    parser = new ArgParser()
    parser.parse(['-o', 'hello'])
    checkMap([other:[], o:'hello'], parser.results)

    parser = new ArgParser()
    parser.parse(['-opt', '-unroll','unroll'])
    checkMap([other:[], opt:['-unroll','unroll']], parser.results)

    parser = new ArgParser()
    parser.parse(['-target','inter'])
    checkMap([other:[], target:'inter'], parser.results)

    parser = new ArgParser()
    parser.parse(['-opt'])
    checkMap([other:[], opt:[]], parser.results)
  }

  void testInvalid() {
    parser = new ArgParser()
    shouldFail{
      parser.parse(['-target','notReal'])
    }
  }
}
