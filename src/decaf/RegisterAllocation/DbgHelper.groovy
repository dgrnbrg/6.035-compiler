package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import static decaf.Reg.eachRegNode

class DbgHelper {
  static boolean dbgValidationOn = false;
  static def dbgOut = { str -> 
    assert str; 
    if(true)
      println str; 
  }
}
