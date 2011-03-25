package decaf;
import java.util.*;
import org.apache.commons.collections.iterators.IteratorChain;

interface JoinOperator<D>{
    public Set<D> joinOp(Set<Set<D>> inputs);
}

abstract class TransitionFunction<D>{
    // By default, transition functions are the identity function.
    // Simply extend this class and override the transition methods
    // for the node types that you want to perform analysis on.
    public Set<D> transition(LowIrValueNode node, Set<D> input){
	return input;
    }
    
    public Set<D> transition(LowIrIntLiteral node, Set<D> input){
	return input;
    }

    public Set<D> transition(LowIrMov node, Set<D> input){
	return input;
    }
    
    public Set<D> transition(LowIrBinOp node, Set<D> input){
	return input;
    }
    
    public Set<D> transition(LowIrReturn node, Set<D> input){
	return input;
    }

    public Set<D> transition(LowIrStringLiteral node, Set<D> input){
	return input;
    }
    public Set<D> transition(LowIrMethodCall  node, Set<D> input){
	return input;
    }
    
    public Set<D> transition(LowIrCallout  node, Set<D> input){
	return input;
    }
    
    public Set<D> transition(LowIrCondJump  node, Set<D> input){
	return input;
    }
}

class DataFlowAnalysis{
    private JoinOperator join;
    private TransitionFunction transition;
    private LowIrNode start;
    
    DataFlowAnalysis(JoinOperator join, TransitionFunction transition, LowIrNode start){
	this.join = join;
	this.transition = transition;
	this.start = start;
    }

    
}