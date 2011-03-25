package decaf;
import decaf.graph.*;
import java.util.*;
import org.apache.commons.collections.iterators.IteratorChain;

interface JoinOperator<D>{
    public Set<D> joinOp(Set<Set<D>> inputs);
}

abstract class TransferFunction<D>{
    // By default, transfer functions are the identity function.
    // Simply extend this class and override the transfer methods
    // for the node types that you want to perform analysis on.
    public Set<D> transfer(LowIrValueNode node, Set<D> input){
	return input;
    }
    
    public Set<D> transfer(LowIrIntLiteral node, Set<D> input){
	return input;
    }

    public Set<D> transfer(LowIrMov node, Set<D> input){
	return input;
    }
    
    public Set<D> transfer(LowIrBinOp node, Set<D> input){
	return input;
    }
    
    public Set<D> transfer(LowIrReturn node, Set<D> input){
	return input;
    }

    public Set<D> transfer(LowIrStringLiteral node, Set<D> input){
	return input;
    }
    public Set<D> transfer(LowIrMethodCall  node, Set<D> input){
	return input;
    }
    
    public Set<D> transfer(LowIrCallOut  node, Set<D> input){
	return input;
    }
    
    public Set<D> transfer(LowIrCondJump  node, Set<D> input){
	return input;
    }
}

class DataFlowAnalysis{
    private JoinOperator join;
    private TransferFunction transfer;
    private LowIrNode start;

    // Graph traversal bookkeeping
    private Set<LowIrNode> nodesVisited;
    private Set<TraverserEdge> edgesVisited;
    
    DataFlowAnalysis(JoinOperator join, TransferFunction transfer){
	this.join = join;
	this.transfer = transfer;
    }

    DataFlowAnalysis(){
	this.join = null;
	this.transfer = null;
    }

    public void traverse(GraphNode begin){
	this.nodesVisited = new HashSet<LowIrNode>();
	this.edgesVisited = new HashSet<TraverserEdge>();

	Queue<GraphNode> workQueue = new LinkedList<GraphNode>();
	workQueue.offer(begin);

	while(workQueue.peek() != null){
	    GraphNode current = workQueue.poll();
	    
	    for(GraphNode successor : current.getSuccessors()){
		workQueue.offer(successor);
	    }
	    
	    System.out.print(">> visited:");
	    System.out.println(current);
	}
    }
    
    public void run(LowIrNode start){
	this.start = start;
	this.traverse((GraphNode)start);
    }
}