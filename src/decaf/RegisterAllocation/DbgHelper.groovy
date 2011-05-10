package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import static decaf.Reg.eachRegNode

class DbgHelper {
  static boolean dbgValidationOn = false;//true;
  static def dbgOut = { str -> 
    println str; 
  }
}
