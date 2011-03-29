package decaf;
import decaf.graph.*;
import java.util.*;
import org.apache.commons.collections.iterators.IteratorChain;

// abstract class Analysis<D>{
//     // The join operator
//     abstract public D joinOp(Set<D> inputs);
    
//     // By default, transfer functions are the identity function.
//     // Simply extend this class and override the transfer methods
//     // for the node types that you want to perform analysis on.
//     public D transfer(LowIrNode node, D input){
// 	return input;
//     }
    
//     public D transfer(LowIrValueNode node, D input){
// 	return input;
//     }
    
//     public D transfer(LowIrIntLiteral node, D input){
// 	return input;
//     }

//     public D transfer(LowIrMov node, D input){
// 	return input;
//     }
    
//     public D transfer(LowIrBinOp node, D input){
// 	return input;
//     }
    
//     public D transfer(LowIrReturn node, D input){
// 	return input;
//     }

//     public D transfer(LowIrStringLiteral node, D input){
// 	return input;
//     }
    
//     public D transfer(LowIrMethodCall  node, D input){
// 	return input;
//     }
    
//     public D transfer(LowIrCallOut  node, D input){
// 	return input;
//     }
    
//     public D transfer(LowIrCondJump  node, D input){
// 	return input;
//     }

//     // Returns 'new D'
//     abstract public D emptyState();
// }

abstract class DataFlowAnalysis{
    // transfer(node) returns true if the analysis data associated
    // with node changes as a side effect of the transfer function.
    abstract public boolean transfer(GraphNode node);
    abstract public void storeOnNode(GraphNode node, Set data);

    // Invariant: loadFromNode should return the empty set should
    // it be ran against an un-annotated node.
    abstract public Set loadFromNode(GraphNode node);
    abstract public Set join(GraphNode node);

    private void analyze(GraphNode node){
	// For forward dataflow analyses
	Queue<GraphNode> workQueue = new LinkedList<GraphNode>();

	workQueue.offer(node);

	while(workQueue != null){
	    GraphNode = workQueue.poll();
	    // If analysis yields different result from before, add
	    // all successor nodes to be processed.
	    if(this.transfer(node) == true){
		for(GraphNode sucessor : node.getSuccessors()){
		    workQueue.offer(successor);
		}
	    }
	}
    }

    public void traverse(GraphNode begin){
	// traverse() actually runs the dataflow functions against
	// every node in the graph, repeatedly, until no new information
	// is output from running the transfer function against any node.

	// BFS is used so that we can always (unless there is a back edge)
	// know the input to the next node we analyze.
	// this.nodesVisited = new HashSet<LowIrNode>();
	// this.edgesVisited = new HashSet<TraverserEdge>();
	Queue<GraphNode> workQueue = new LinkedList<GraphNode>();
	LinkedList<GraphNode> nodes = this.initializeNodes(begin);

	for(GraphNode node : nodes){
	    // Add successors to work list if a node's output changes
	    D prevInput = this.input.get(node);
	    D output = this.dispatchTransfer(node);
	    if(!prevInput.equals(output)){
		this.input.put(node, output);
		
		for(GraphNode successor : node.getSuccessors()){
		    workQueue.offer(successor);
		}
	    }
	}

	while(workQueue.peek() != null){
	    GraphNode node = workQueue.poll();
	    
	    System.out.print(">>");
	    System.out.println(node);
	    
	    D prevInput = this.input.get(node);
	    D output = this.dispatchTransfer(node);
	    if(!prevInput.equals(output)){
		System.out.print(">> analysis returned new output:");
		System.out.println(output);
		
		this.input.put(node, output);
		
		for(GraphNode successor : node.getSuccessors()){
		    workQueue.offer(successor);
		}
	    }
	}
    }
}

