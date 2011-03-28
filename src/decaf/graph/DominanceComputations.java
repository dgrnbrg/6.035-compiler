package decaf.graph;
import java.util.*;

public class DominanceComputations {
  private Map<GraphNode, Set<GraphNode>> domFrontier;
  //This is a representation of the dominance tree
  //it allows for efficient things I'll make up
  private Map<GraphNode, Set<GraphNode>> domTree;
  //used by lengauer-tarjan
  private Map<GraphNode, Integer> dfNum;
  private Map<Integer, GraphNode> vertex;
  private Map<GraphNode, GraphNode> parent, semi, idom, samedom, ancestor;
  private Map<GraphNode, Set<GraphNode>> bucket, idomReverse;
  private int N;

  private void init() {
    N = 0;
    parent = new HashMap<GraphNode, GraphNode>();
    semi = new HashMap<GraphNode, GraphNode>();
    idom = new HashMap<GraphNode, GraphNode>();
    samedom = new HashMap<GraphNode, GraphNode>();
    ancestor = new HashMap<GraphNode, GraphNode>();
    vertex = new HashMap<Integer, GraphNode>();
    dfNum = new HashMap<GraphNode, Integer>();
    bucket = new HashMap<GraphNode, Set<GraphNode>>();
    idomReverse = new HashMap<GraphNode, Set<GraphNode>>();
    domTree = new HashMap<GraphNode, Set<GraphNode>>();
    domFrontier = new HashMap<GraphNode, Set<GraphNode>>();
  }

  private final boolean doesDominate(GraphNode dom, GraphNode child) {
    while (dom != child && (child = ancestor.get(child)) != null);
    //terminates when dom == child or we hit the root
    return dom == child;
  }

  //see page 406 of modern compiler implementation in Java for this algorithm
  //immediate dominators are on page 380
  private void computeDominanceFrontier(GraphNode n) {
    if (domFrontier.get(n) != null) { return; }
    Set<GraphNode> s = new HashSet<GraphNode>();
    for (GraphNode y : n.getSuccessors()) {
      if (idom.get(y) != n) {
        s.add(y);
      }
    }
    Queue<GraphNode> children;
    if (domTree.containsKey(n)) {
      children = new LinkedList<GraphNode>(domTree.get(n));
    } else {
      children = new LinkedList<GraphNode>();
    }
    while (!children.isEmpty()) {
      GraphNode c = children.remove();
      if (domTree.containsKey(c)) {
        children.addAll(domTree.get(c));
      }
      computeDominanceFrontier(c);
      for (GraphNode w : domFrontier.get(c)) {
        if (!(idom.get(w) == n) || n == w) {
          s.add(w);
        }
      }
    }
    domFrontier.put(n, s);
  }

  private void doDFS(GraphNode p, GraphNode n) {
    Integer result = dfNum.get(n);
    if (result == null || result == 0) {
      dfNum.put(n, N);
      vertex.put(N, n);
      parent.put(n, p);
      N++;
      for (GraphNode w : n.getSuccessors()) {
        doDFS(n, w);
      }
    }
  }

  private void computeDominators(GraphNode r) {
    init();
    doDFS(null, r);
    for (int i = N - 1; i > 0; i--) {
      assert i != 0; //verify I did this reverse count correctly
      GraphNode n = vertex.get(i);
      GraphNode p = parent.get(n);
      GraphNode s = p;
      //compute semidominator
      for (GraphNode v : n.getPredecessors()) {
        GraphNode s_prime;
        if (!dfNum.containsKey(v)) {
          //conceptually, this is a piece of dead code
          continue;
        }
        if (dfNum.get(v) <= dfNum.get(n)) {
          s_prime = v;
        } else {
          s_prime = semi.get(getAncestorWithLowestSemi(v));
        }
        if (dfNum.get(s_prime) < dfNum.get(s)) {
          s = s_prime;
        }
      }
      semi.put(n, s);
      //calculation of n's dominator is deferred until the path from s to n has
      //been linked into the forest
      getHashSet_lazyInit(bucket, s).add(n);
      doLink(p, n);
      //calculate the dominator of v
      for (GraphNode v : getHashSet_lazyInit(bucket, p)) {
        GraphNode y = getAncestorWithLowestSemi(v);
        if (semi.get(y) == semi.get(v)) {
          idom.put(v,p);
          getHashSet_lazyInit(idomReverse, v).add(p);
        } else {
          samedom.put(v,y);
        }
      }
      bucket.put(p, new HashSet<GraphNode>());
    }
    for (int i = 1; i < N; i++) {
      GraphNode n = vertex.get(i);
      if (samedom.get(n) != null) {
        idom.put(n, idom.get(samedom.get(n)));
        getHashSet_lazyInit(idomReverse, n).add(idom.get(samedom.get(n)));
      }
    }
  }

  //this is not the path compression implementation
  //that would improve this from O(n) to O(logn)
  private GraphNode getAncestorWithLowestSemi(GraphNode v) {
    GraphNode u = v;
    while (ancestor.get(v) != null) {
      if (dfNum.get(semi.get(v)) < dfNum.get(semi.get(u))) {
        u = v;
      }
      v = ancestor.get(v);
    }
    return u;
  }

  private void doLink(GraphNode p, GraphNode n) {
    ancestor.put(n, p);
    getHashSet_lazyInit(domTree, p).add(n);
  }

  //final encourages inlining by the JIT
  private final static <K, V> Set<V> getHashSet_lazyInit(Map<K,Set<V>> map, K key) {
    if (map.containsKey(key)) {
      return map.get(key);
    } else {
      Set<V> set = new HashSet<V>();
      map.put(key, set);
      return set;
    }
  }
}
