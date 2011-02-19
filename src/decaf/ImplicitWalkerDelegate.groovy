package decaf

class ImplicitWalkerDelegate {
  Closure walk
  def properties = [:]

  void declVar(String property, value = null) {
    if (properties[property])
      throw new MissingPropertyException("Property already defined: $property")
    properties[property] = value
  }

  void setProperty(String property, newValue) {
    if (property == 'walk') {
      walk = newValue
    } else if (!properties[property]) {
      throw new MissingPropertyException("Unable to find property $property")
    } else {
      properties[property] = newValue
    }
  }

  def getProperty(String property) {
    if (!properties[property])
      throw new MissingPropertyException("Unable to find property $property")
    return properties[property]
  }
}
