package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*
import decaf.optimizations.*

class SpillVar extends TempVar {
  static int nextSpillId = 0;

  static int getNextID() {
    nextSpillId += 1;
    return nextSpillId;
  }

  SpillVar() {
    id = getNextID();
    type = TempVarType.SPILLVAR
  }
  
  String toString() {
    return "[SV. id = $id]"
    //return "[SpillVar. id = $id]"
  }
}

class SpillVarManager {
  List<SpillVar> lineup = [] // These are the spill vars for whom space will be allocated on the stack.
  LinkedHashMap svLocMap = [:];
  LinkedHashMap preservedRegSlots;

  public SpillVarManager() {
    // Important: Here is where we create our registers
    def preservedRegNames = 
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15'];
    preservedRegSlots = [:];
    lineup = [];
    svLocMap = [:];

    preservedRegNames.each { prn -> 
      preservedRegSlots[(prn)] = requestNewSpillVar();
    }
  }

  SpillVar requestNewSpillVar() {
    SpillVar sv = new SpillVar();
    svLocMap[(sv)] = lineup.size();
    lineup += [sv]
    assert lineup.size() == svLocMap.keySet().size();
    return sv;
  }

  int getNumSpillVarsToAllocate() {
    return lineup.size();
  }

  int getLocOfSpillVar(SpillVar sv) {
    assert sv; assert svLocMap[(sv)] != null;
    return svLocMap[(sv)];
  }

  SpillVar getPreservedSpillVarFor(String regName) {
    assert regName; assert preservedRegSlots.keySet().contains(regName);
    return preservedRegSlots[(regName)];
  }
}
