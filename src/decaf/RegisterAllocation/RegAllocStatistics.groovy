package decaf

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
}
