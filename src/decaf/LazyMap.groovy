package decaf

class LazyMap {
  Closure factory
  @Delegate Map map = [:]

  LazyMap(Closure factory) {
    this.factory = factory
  }

  Object getAt(Object key) {
    if (!map.containsKey(key)) {
      map[key] = factory(key)
    }
    return map[key]
  }
}
