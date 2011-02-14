package decaf
import groovytools.builder.*

public class ArgParser {
  public static final int ARGS_UNLIMITED = -1;
  MetaBuilder mb = new MetaBuilder()
  def argStruct
  def results = [other: []]

  public ArgParser() {
    mb.define {
      program {
        collections {
          options {
            '%' {
              properties {
                count(req: true, check: { it.class == Integer })
                restrict(check: { it instanceof List })
              }
            }
          }
        }
      }
    }
    def tmp = mb.build {
      program {
        options {
          o(count: 1)
          target(count: 1, restrict: ['scan', 'parse', 'inter', 'assembly'])
          opt(count: -1, restrict: ['-?unroll'])
          debug(count: 0)
        }
      }
    }
    argStruct = tmp['options'][0]
  }

  def getAt(String x) {
    return results[x]
  }

  def parse(args) {
    def chomp = {
      if (args == []) {
        throw new RuntimeException("unexpected end of argument list")
      }
      def tmp = args[0]
      args = args.size != 1 ? args[1 .. args.size-1] : []
      return tmp
    }
    while (args != []) {
      String argString = chomp()
      if (argString ==~ /[^-].*/) {
        results['other'] << argString
        continue
      }
      def schema = argStruct[argString.substring(1)][0]
      if (schema == null) {
        throw new RuntimeException("unknown argument $argString")
      }
      //get an attribute with @attr
      def argCount = schema['@count']
      if (argCount == ARGS_UNLIMITED) {
        results[schema.name()] = []
        if (schema['@restrict'] == null) {
          throw new RuntimeException("no restricted set of keywords set for argument $argString")
        }
        while (schema['@restrict'].any{args[0] ==~ it}) {
          results[schema.name()] << chomp()
        }
        continue
      }
      //finite number of args
      def checkRestrict = { arg ->
        if (schema['@restrict'] && !schema['@restrict'].any{arg ==~ it})
          throw new RuntimeException("cannot pass $arg to $argString")
        return arg
      }
      if (argCount == 1) {
        results[schema.name()] = checkRestrict(chomp())
      } else {
        results[schema.name()] = []
        argCount.times {
          results[schema.name()] << checkRestrict(chomp())
        }
      }
    }
  }

  public static void main(args) {
    def p = new ArgParser()
    p.parse(['-o','tmp.decaf','-opt','unroll','-unroll','blah','-target','inter','foo','-debug'])
    println p.results
    println p['o']
  }
}
