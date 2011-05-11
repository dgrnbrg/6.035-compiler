package decaf.RegisterAllocation

import groovy.util.*
import decaf.*
import decaf.graph.*
import static decaf.Reg.eachRegNode

public class RegAllocStatistics {
  static LinkedHashSet GetTempVarsStoredToSpills(MethodDescriptor methodDesc) {
    def tvSTS = new LinkedHashSet();
    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      if(node instanceof LowIrStoreSpill)
        tvSTS << node.value
    }
    return tvSTS;
  }

  static LinkedHashSet GetTempVarsLoadedFromSpills(MethodDescriptor methodDesc) {
    def tvLFS = new LinkedHashSet();
    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      if(node instanceof LowIrLoadSpill)
        tvLFS << node.tmpVar
    }
    return tvLFS;
  }

  static LinkedHashSet GetLowIrIntLiteralRelatedTempVars(MethodDescriptor methodDesc) {
    def tvLIILR = new LinkedHashSet();
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrIntLiteral) {
        tvLIILR << node.tmpVar
      }
    }

    return tvLIILR;
  }
  
  static LinkedHashMap GetUsedLittle(MethodDescriptor methodDesc) {
    def tvToNumUses = new LazyMap({0});
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrIntLiteral)
        tvToNumUses[node] += 1;
    }
    
    return tvToNumUses;
  }
  
  static LinkedHashSet GetAllUses(MethodDescriptor methodDesc) {
    def allUses = [];
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node.uses())
        node.uses.each { allUses << it }
    }
    return allUses
  }
  
  static LinkedHashSet GetAllDefs(MethodDescriptor methodDesc) {
    def allDefs
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node.def())
        allDefs << it
    }
    return allDefs
  }
  
  static LinkedHashMap GetFarApart(MethodDescriptor methodDesc) {
    def tvToLongShort = new LazyMap({0});
  }
}

/*
Spill Heuristic Notes:

spill when used little, far apart

longest-shortest path
*/