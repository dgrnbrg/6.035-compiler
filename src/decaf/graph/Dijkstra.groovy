package decaf.graph
import decaf.LazyMap

class Dijkstra {
  def initialVal = 10000000000000
  def dist = new LazyMap({initialVal})
  def prev = [:]

  //usage: new Dijkstra(methodDesc.lowir, sourceNode, {it.predecessors + it.successors})
  //usage: new Dijkstra(methodDesc.lowir, sourceNode, {it.successors})

  Dijkstra(startNode, src, neighbors) {
    def q = new TreeSet({u, v -> u == v ? 0 : (dist[u] < dist[v] ? -1 : 1)} as Comparator)
    dist[src] = 0
    Traverser.eachNodeOf(startNode){q << it}
    while (!q.isEmpty()) {
      def u = q.iterator().first()
      if (dist[u] == initialVal) break
      q.remove(u)
      for (v in neighbors(u)) {
        def alt = dist[u] + 1
        if (alt < dist[v]) { //relax
          q.remove(v)
          dist[v] = alt
          q << v
          prev[v] = u
        }
      }
    }
  }
}
