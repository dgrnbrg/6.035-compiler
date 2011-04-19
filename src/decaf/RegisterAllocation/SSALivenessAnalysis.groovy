package decaf

import groovy.util.*
import decaf.*
import decaf.graph.*

// This is a recursive version of Liveness Analysis based on MICJ SSA Chap. 
// It is modified to not rely on basic-blocks and handle phi-functions correctly
class SSALivenessAnalysis {
	MethodDescriptor methodDesc;
  LinkedHashSet<TempVar> M;
  def defToStmt;
  def UseSites;
  LinkedHashSet<TempVar> variables
  def liveIn;
  def liveOut;
  
  public SSALivenessAnalysis(MethodDescriptor md) {
  	assert(md)
  	methodDesc = md
  }
  
  void computeVariables() {
    variables = new LinkedHashSet<TempVar>()
    
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      if(node.getDef() != null) 
        variables += new LinkedHashSet([node.getDef()])
    }
  }
  
  void computeUseSitesOfVariables() {
    UseSites = [:]
    defToStmt = [:]
    
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      node.getUses().each { v -> 
        if(v.type != TempVarType.PARAM) {
          if(!UseSites[(v)]) 
            UseSites[(v)] = new LinkedHashSet()
          UseSites[(v)] += new LinkedHashSet([node])
        }
      }
      
      // While we're at it, lets build up defToStmt too
      if(node.getDef()) {
        // Since this is SSA form, we should not have seen this def 
        // before.
        assert(defToStmt[(node.getDef())] == null)
        defToStmt[(node.getDef())] = node
      }
    }
  }
  
  void RunLivenessAnalysis() {
    println "Now running liveness analysis."    
    
    computeVariables()
    
    println "The variables used are:"
    computeUseSitesOfVariables()
    
    println "The use sites are:"
    UseSites.keySet().each { k -> 
      println "  Var = $k"
      UseSites[(k)].each { v -> 
        println "    $v"
      }
    }
    
    // Prepare the live in and live out.
    liveIn = [:]
    liveOut = [:]
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      liveIn[(node)] = new LinkedHashSet()
      liveOut[(node)] = new LinkedHashSet()
    }
    
    variables.each { v -> 
      M = new LinkedHashSet()
      UseSites[(v)].each { s -> 
        if(s instanceof LowIrPhi) {
          HandlePhiCase(s, v)
        } else {
          LiveInAtStatement(s, v)
        }
      }
    }
    
    println "Now for LiveIn and LiveOut for each node:"
    Traverser.eachNodeOf(methodDesc.lowir) { node -> 
      println "Node = $node"
      println " LiveIn = "
      if(liveIn[(node)])
        liveIn[(node)].each { 
          println "   $it"
        }
      
      println " LiveOut = "
      if(liveOut[(node)])
        liveOut[(node)].each {
          println "   $it"
      }
    }
  }
  
  boolean HandlePhiCase(LowIrNode s, TempVar v) {
    assert(defToStmt); assert(s); assert(v);
    
    println "Handling a Phi Case!"
    
    // We need to walk down the block containing the def of v.
    assert(defToStmt[(v)])
    assert(defToStmt[(v)].getDef() == v)
    def curNode = defToStmt[(v)]
    def phiDef = s.getDef()

    liveOut[(curNode)] += new LinkedHashSet([phiDef])
    
    if(!isEndOfBlock(curNode)) {
      while(true) {
        assert(curNode.getSuccessors().size() == 1)
        curNode = curNode.getSuccessors().first()
        liveIn[(curNode)] += new LinkedHashSet([phiDef])
        liveOut[(curNode)] += new LinkedHashSet([phiDef])
        
        if(isEndOfBlock(curNode))
          break;
      }
    }
    
    assert(isEndOfBlock(curNode))
    
    // Make sure we don't visit this block again for this variable
    M += new LinkedHashSet([curNode])
    
    // now walk up the block containing s.
    curNode = s
    liveIn[(curNode)] += new LinkedHashSet([phiDef])
    
    if(!isBeginningOfBlock(curNode)) {
      while(true) {
        assert(curNode.getPredecessors().size() == 1)
        curNode = curNode.getPredecessors().first()
        liveOut[(curNode)] += new LinkedHashSet([phiDef])
        liveIn[(curNode)] += new LinkedHashSet([phiDef])
        
        if(isBeginningOfBlock(curNode))
          break;
      }
    }
    
    assert(isBeginningOfBlock(curNode))    
  }
  
  boolean isBeginningOfBlock(LowIrNode node) {
    assert(node)

    switch(node.getPredecessors().size()) {
    case 0: 
      return true; // Beginning of cfg
    case 1:
      // Check if it is a conditional branch
      return (node.getPredecessors().first() instanceof LowIrCondJump)
    default: 
      return true; // Is a join point
    }
  }
  
  boolean isEndOfBlock(LowIrNode node) {
    assert(node)

    switch(node.getSuccessors().size()) {
    case 0: 
      return true // end of cfg
    case 1: 
      // Check if the successor is a joint point
      if(node.getSuccessors().first().getSuccessors().size() > 1)
        return true
      return false
    case 2:
      // This should be a cond jump
      assert(node instanceof LowIrCondJump)
      return true
    default:
      assert(false)
    }
  }

  void LiveInAtStatement(LowIrNode s, TempVar v) {
  	assert(s); assert(v); assert(liveIn);
  	liveIn[(s)] += new LinkedHashSet([v])
  	
  	if(isBeginningOfBlock(s)) {
  		s.getPredecessors().each { node -> 
  			LiveOutAtStatement(node, v)
      }
  	}
  }
  
  void LiveOutAtStatement(LowIrNode s, TempVar v) {
  	assert(s); assert(v); assert(liveOut);
  	liveOut[(s)] += new LinkedHashSet([v]);

  	if(isEndOfBlock(s)) {
  		if(M.contains(s) == false)
  			M += new LinkedHashSet([s])
  		else
  			return // Already visited this "block"
  	}
    
    TempVar W = s.getDef()
    
    if(W != v) 
      LiveInAtStatement(s, v)
  }
}
