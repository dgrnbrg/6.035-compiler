package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

public class RegAllocLowIrModifier {
  static void PlaceSpillStoreAfterNode(LowIrNode siteOfSpill, SpillVar sv, TempVar tv) {
    assert siteOfSpill; assert tv; assert sv;
    PlaceNodeAfterNode(siteOfSpill, new LowIrStoreSpill(value : tv, storeLoc : sv));
  }

  static void PlaceSpillLoadBeforeNode(LowIrNode siteOfSpill, SpillVar sv, TempVar tv) {
    assert siteOfSpill; assert tv; assert sv;
    PlaceNodeBeforeNode(new LowIrLoadSpill(tmpVar : tv, loadLoc : sv), siteOfSpill);
  }

  static void MarkUselessIntLiterals(MethodDescriptor methodDesc) {
    // First get all the uses in the program.
    LinkedHashSet<TempVar> uses = [];
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node.getUses())
        node.getUses().each { uses << it }
    }

    // Now get rid of useless int literals.
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrIntLiteral) {
        assert node.useless == false;
        if(uses.contains(node.getDef()) == false)
          node.useless = true;
      }
    }
  }

  static void BreakCalls(MethodDescriptor methodDesc) {
    // First we'll get rid of the past-6 parameters.
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrCallOut || node instanceof LowIrMethodCall)
        if(node.paramTmpVars.size() > 6)
          PlaceStackMovesBeforeCall(node);
    }

    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrCallOut || node instanceof LowIrMethodCall)
        assert node.paramTmpVars.size() <= 6;
    }

    // Now break duplicates within the first 6.
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node instanceof LowIrCallOut || node instanceof LowIrMethodCall) {
        List<TempVar> newParams = node.paramTmpVars.collect { ptv -> 
          TempVar newTV = methodDesc.tempFactory.createLocalTemp();
          PlaceNodeBeforeNode(new LowIrMov(src : ptv, dst : newTV), node);
          return newTV
        }

        node.paramTmpVars = newParams
      }
    }
  }

  static void PlaceStackMovesBeforeCall(LowIrNode node) {
    assert (node instanceof LowIrCallOut) || (node instanceof LowIrMethodCall);
    assert node.paramTmpVars.size() > 6;

    node.numOriginalArgs = node.paramTmpVars.size();

    println "size is now: ${node.paramTmpVars.size()}"

    while(node.paramTmpVars.size() > 6) {
      int pos = node.paramTmpVars.size()
      node.paramTmpVars = node.paramTmpVars.toList();
      TempVar tv = node.paramTmpVars.toList().last();
      node.paramTmpVars = (node.paramTmpVars.toList())[0..(node.paramTmpVars.size() - 2)];
      LowIrLoadArgOntoStack argMov = new LowIrLoadArgOntoStack(tv, pos);
      PlaceNodeBeforeNode(argMov, node);
    }

    assert node.paramTmpVars.size() == 6;
  }

  static void BreakDivMod(MethodDescriptor methodDesc) {
    Traverser.eachNodeOf(methodDesc.lowir) { node ->
      if(node instanceof LowIrBinOp && (node.op == BinOpType.DIV || node.op == BinOpType.MOD)) {
        TempVar newLeftVar = methodDesc.tempFactory.createLocalTemp();
        PlaceNodeBeforeNode(new LowIrMov(src : node.leftTmpVar, dst : newLeftVar), node);
        node.leftTmpVar = newLeftVar;

        TempVar newRightVar = methodDesc.tempFactory.createLocalTemp();
        PlaceNodeBeforeNode(new LowIrMov(src : node.rightTmpVar, dst : newRightVar), node);
        node.rightTmpVar = newRightVar;

        TempVar newOutputVar = methodDesc.tempFactory.createLocalTemp();
        println "Doing it!: src: $newOutputVar, dst : ${node.tmpVar}"
        PlaceNodeAfterNode(node, new LowIrMov(src : newOutputVar, dst : node.tmpVar));
        node.tmpVar = newOutputVar
      }
    }
  }

  // Warning, this function should not be called on LowIrCondJump.
  static void PlaceNodeAfterNode(LowIrNode beforeNode, LowIrNode afterNode) {
    assert beforeNode; assert afterNode
    assert !(beforeNode instanceof LowIrCondJump)
    assert beforeNode.getSuccessors().size() == 1
    
    LowIrNode nodeToMoveBefore = beforeNode.getSuccessors().first()
    LowIrNode.link(beforeNode, afterNode)
    LowIrNode.link(afterNode, nodeToMoveBefore)
    LowIrNode.unlink(beforeNode, nodeToMoveBefore)
  }

  static void PlaceNodeBeforeNode(LowIrNode beforeNode, LowIrNode afterNode) {
    assert beforeNode; assert afterNode;
    assert afterNode.getPredecessors().size() > 0;

    LowIrNode.link(beforeNode, afterNode);
    def pred = afterNode.getPredecessors();
    pred.each { p -> 
      LowIrNode.unlink(p, afterNode);
      LowIrNode.link(p, beforeNode);
      if(p instanceof LowIrCondJump) {
        if(p.trueDest == afterNode) 
          p.trueDest = beforeNode;
        if(p.falseDest == afterNode)
          p.falseDest = beforeNode;
      }
    }
  }
}
