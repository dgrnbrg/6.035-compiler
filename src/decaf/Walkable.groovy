package decaf

interface Walkable {
  void inOrderWalk(Closure c);
}

abstract class WalkableImpl implements Walkable {
  ImplicitWalkerDelegate walkerDelegate = new ImplicitWalkerDelegate()

  abstract void howToWalk(Closure c);

  void inOrderWalk(Closure c) {
    walkerDelegate.walk = this.&howToWalk.curry(c)
    Closure c2 = c.clone()
    c2.delegate = walkerDelegate
    c2(this)
  }

  def propertyMissing(String propName) {
    return walkerDelegate."$propName"
  }
}

class ImplicitWalkerDelegate {
  Closure walk
  def properties = [:]

  void declVar(String property, value = null) {
    if (properties.containsKey(property))
      throw new MissingPropertyException("Property already defined: $property")
    properties[property] = value
  }

  void setProperty(String property, newValue) {
    if (property == 'walk') {
      walk = newValue
    } else if (!properties.containsKey(property)) {
      throw new MissingPropertyException("Unable to find property $property")
    } else {
      properties[property] = newValue
    }
  }

  def getProperty(String property) {
    if (!properties.containsKey(property))
      throw new MissingPropertyException("Unable to find property $property")
    return properties[property]
  }
}
