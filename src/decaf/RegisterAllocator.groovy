package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*


/* ASSUMPTIONS:
	1. I assume that all parameter tempVars will be explicitly loaded 
			from memory into a register. To do this, I essentially auto-spill 
			all the paramTempVars.
*/

class RegisterAllocator {
	MethodDescriptor methodDesc;
  InterferenceGraph ig;
  
  public RegisterAllocator(MethodDescriptor md) {
    assert(md)
    methodDesc = md
  }
  
  public ComputeInterferenceGraph() {
  	ig = new InterferenceGraph(methodDesc)
  	ig.CalculateInterferenceGraph()
    ig.ColorGraph(16)
  }
}
