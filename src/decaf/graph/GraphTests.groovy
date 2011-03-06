package decaf.graph

class Node implements GraphNode {
  List<Node> predecessors = [], successors = []

  String text

  public String toString() { text }

  public List<GraphNode> getPredecessors() {
    return this.@predecessors
  }
  public List<GraphNode> getSuccessors() {
    return this.@successors
  }
}

def link(a, b) {
  b.predecessors << a
  a.successors << b
}

def a = new Node(text: 'a')
def b = new Node(text: 'b')
def c = new Node(text: 'c')
def d = new Node(text: 'd')
def e = new Node(text: 'e')
def f = new Node(text: 'f')
def g = new Node(text: 'g')
def h = new Node(text: 'h')
def i = new Node(text: 'i')
def j = new Node(text: 'j')
def k = new Node(text: 'k')
def l = new Node(text: 'l')
def m = new Node(text: 'm')

//graph from page 411 in book
//forward edges to m
link(a,b)
link(a,c)
link(b,d)
link(c,e)
link(d,f)
link(d,g)
link(e,h)
link(f,i)
link(f,k)
link(g,j)
link(j,i)
link(i,l)
link(k,l)
link(l,m)
link(h,m)
//other edges
link(l,b)
link(e,c)
link(c,h)
link(b,g)

class DotTrav extends Traverser {
  void visitNode(GraphNode n) {}
  void link(GraphNode src, GraphNode dst) { println "$src.text -> $dst.text" }
}

def comp = new DominanceComputations()
try {
println "digraph g {"
/*
comp.computeDominators(a);
comp.idom.each{key, val ->
 println "$key.text -> $val.text"
}
*/
def t = new DotTrav()
t.reset()
t.traverse(a)
println "}"


} catch (Throwable err) {
      def skipPrefixes = ['org.codehaus','sun.reflect','java.lang.reflect','groovy.lang.Meta']
      def st = err.getStackTrace().findAll { traceElement ->
        !skipPrefixes.any { prefix ->
          traceElement.getClassName().startsWith(prefix)
        }
      }
      println err
      st.each {
        def location = it.getFileName() != null ? "${it.getFileName()}:${it.getLineNumber()}" : 'Unknown'
        println "  at ${it.getClassName()}.${it.getMethodName()}($location)"
      }
}
