package decaf

public class ArgParser {
  public static final int ARGS_UNLIMITED = -1;
  def argStruct
  def results = [other: []]

  public ArgParser() {
    def nb = new NodeBuilder()
    argStruct = nb.options {
      o(count: 1)
      target(count: 1, restrict: ['scan', 'parse', 'inter', 'assembly','hiir','antlrast','symtable','inter', 'lowir', 'codegen'])
      assertEnabled(count: 0)
      opt(count: -1, restrict: ['cse', 'ssa', 'all', 'cp', 'dce', 'inline', 'sccp', 'pre', 'dse', 'iva', 'peep', 'regalloc', 'unroll'])
      debug(count: 0)
    }
  }

  def getAt(String x) {
    return results[x]
  }

  def parse(List args) {
    def chomp = {
      if (args == []) {
        throw new RuntimeException("unexpected end of argument list")
      }
      def tmp = args[0]
      args = args.size() != 1 ? args[1 .. args.size()-1] : []
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
        throw new RuntimeException("unknown argument \"$argString\"")
      }
      //get an attribute with @attr
      def argCount = schema['@count']
      if (argCount == ARGS_UNLIMITED) {
        results[schema.name()] = []
        if (schema['@restrict'] == null) {
          throw new RuntimeException("no restricted set of keywords set for argument \"$argString\"")
        }
        while (schema['@restrict'].any{args[0] ==~ it}) {
          results[schema.name()] << chomp()
        }
        continue
      }
      //finite number of args
      def checkRestrict = { arg ->
        if (schema['@restrict'] && !schema['@restrict'].any{arg ==~ it})
          throw new RuntimeException("cannot pass \"$arg\" to \"$argString\"")
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
//    p.parse(['-o','tmp.decaf','-opt','unroll','-unroll','blah','-target','inter','foo','-debug'])
    p.parse(['legal-04','-target','parser'])
    println p.results
    println p['o']
  }
}
