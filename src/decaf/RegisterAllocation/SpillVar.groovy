package decaf.RegisterAllocation

import groovy.util.*
import decaf.*
import decaf.graph.*
import decaf.optimizations.*

public class SpillVar extends TempVar {
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

class PostSixParamSpillVar extends SpillVar {
  int paramPosition = 0;

  String toString() {
    return "[PostSixParamSV. paramPosition = 0]";
  }
}

class SpillVarManager {
  List<SpillVar> lineup = [] // These are the spill vars for whom space will be allocated on the stack.
  LinkedHashMap svLocMap = [:];
  LinkedHashMap preservedRegSlots;
  LinkedHashMap firstSixFlags;
  LinkedHashMap postSixSpillFlags;
  LinkedHashMap postSixColorFlags;

  public SpillVarManager() {
    // Important: Here is where we create our registers
    def preservedRegNames = 
      ['rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'r8', 
       'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15'];
    preservedRegSlots = [:];
    lineup = [];
    svLocMap = [:];
    firstSixFlags = [:];
    postSixSpillFlags = [:];
    postSixColorFlags = [:];

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

  void FlagOneOfFirstSixArgsForSpilling(TempVar paramTV) {
    println "BLADDDDDDDDDDDDDDDDDDD paramTV = $paramTV"
    assert paramTV.type == TempVarType.PARAM
    assert (0 <= paramTV.id && paramTV.id < 6);
    assert firstSixFlags != null;
    assert firstSixFlags.keySet().size() <= 6;
    assert firstSixFlags.keySet().contains(paramTV) == false;
    firstSixFlags[paramTV] = requestNewSpillVar();
    println "and the result is the sv = ${firstSixFlags[paramTV]}"
  }

  void FlagOneOfPostSixArgsForSpilling(TempVar paramTV) {
    assert paramTV.type == TempVarType.PARAM
    assert paramTV.id >= 6;
    assert postSixSpillFlags != null;
    assert postSixSpillFlags.keySet().contains(paramTV) == false;
    postSixSpillFlags[paramTV] = new PostSixParamSpillVar();
    postSixSpillFlags[paramTV].paramPosition = paramTV.id;
  }

  void FlagOneOfPostSixArgsForColoring(TempVar paramTV, Reg color) {
    assert paramTV.type == TempVarType.PARAM
    assert paramTV.id >= 6;
    assert postSixColorFlags != null;
    assert postSixColorFlags.keySet().contains(paramTV) == false;
    postSixColorFlags[paramTV] = color;
  }
}



















