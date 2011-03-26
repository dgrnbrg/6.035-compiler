package decaf;
import decaf.graph.*;
import java.util.*;
import org.apache.commons.collections.iterators.IteratorChain;

abstract class Analysis<D>{
    // The join operator
    abstract public D joinOp(Set<D> inputs);
    
    // By default, transfer functions are the identity function.
    // Simply extend this class and override the transfer methods
    // for the node types that you want to perform analysis on.
    public D transfer(LowIrNode node, D input){
	return input;
    }
    public D transfer(LowIrValueNode node, D input){
	return input;
    }
    
    public D transfer(LowIrIntLiteral node, D input){
	return input;
    }

    public D transfer(LowIrMov node, D input){
	return input;
    }
    
    public D transfer(LowIrBinOp node, D input){
	return input;
    }
    
    public D transfer(LowIrReturn node, D input){
	return input;
    }

    public D transfer(LowIrStringLiteral node, D input){
	return input;
    }
    
    public D transfer(LowIrMethodCall  node, D input){
	return input;
    }
    
    public D transfer(LowIrCallOut  node, D input){
	return input;
    }
    
    public D transfer(LowIrCondJump  node, D input){
	return input;
    }

    // Returns 'new D'
    abstract public D emptyState();
}

class DataFlowAnalysis<D>{
    private Analysis analysis;
    private LowIrNode start;

    // Graph traversal bookkeeping
    private Set<LowIrNode> nodesVisited;
    private Set<TraverserEdge> edgesVisited;

    private Map<GraphNode,D> input;
    
    DataFlowAnalysis(Analysis<D> analysis){
	this.input = new HashMap<GraphNode,D>();
	this.analysis = analysis;
    }

    // DataFlowAnalysis(){
    // 	this.join = null;
    // 	this.transfer = null;
    // }

    private void initializeNode(GraphNode node){
        if(!this.input.containsKey(node)){
	    D inputState = (D)this.analysis.emptyState();
	    // System.out.print("inputState:");
	    // System.out.println(inputState);
	    this.input.put(node, inputState);
	}
    }

    private D getNodeInput(GraphNode node){
        D inputState = (D)this.input.get(node);
	
	return inputState;
    }

    private D dispatchTransfer(GraphNode node){
	// Get the set of inputs to be sent to the join operator
	Set<D> inputs = new HashSet<D>();
	for(GraphNode predecessor : node.getPredecessors()){
	    inputs.add(this.getNodeInput(predecessor));
	}
	    
	D inputState = (D)this.analysis.joinOp(inputs);
	    
	D transferOutput = null;
	if(node instanceof LowIrValueNode){
	    transferOutput = (D)this.analysis.transfer((LowIrValueNode)node, inputState);
	}
	else if(node instanceof LowIrIntLiteral){
	    transferOutput = (D)this.analysis.transfer((LowIrIntLiteral)node, inputState);
	}
	else if(node instanceof LowIrMov){
	    transferOutput = (D)this.analysis.transfer((LowIrMov)node, inputState);
	}
	else if(node instanceof LowIrBinOp){
	    transferOutput = (D)this.analysis.transfer((LowIrBinOp)node, inputState);
	}
	else if(node instanceof LowIrReturn){
	    transferOutput = (D)this.analysis.transfer((LowIrReturn)node, inputState);
	}
	else if(node instanceof LowIrStringLiteral){
	    transferOutput = (D)this.analysis.transfer((LowIrStringLiteral)node, inputState);
	}
	else if(node instanceof LowIrMethodCall){
	    transferOutput = (D)this.analysis.transfer((LowIrMethodCall)node, inputState);
	}
	else if(node instanceof LowIrCallOut){
	    transferOutput = (D)this.analysis.transfer((LowIrCallOut)node, inputState);
	}
	else if(node instanceof LowIrCondJump){
	    transferOutput = (D)this.analysis.transfer((LowIrCondJump)node, inputState);
	}
	else if(node instanceof LowIrNode){
	    transferOutput = (D)this.analysis.transfer((LowIrNode)node, inputState);
	}
	System.out.println(transferOutput);
	
	return transferOutput;
    }

    private LinkedList<GraphNode> initializeNodes(GraphNode begin){
	LinkedList<GraphNode> nodes = new LinkedList<GraphNode>();
	Queue<GraphNode> workQueue = new LinkedList<GraphNode>();	
	workQueue.offer(begin);

	// First run of the analysis processes every node unconditionally
	while(workQueue.peek() != null){
	    GraphNode current = workQueue.poll();
	    this.initializeNode(current);
	    nodes.addLast(current);

	    for(GraphNode successor : current.getSuccessors()){
		workQueue.offer(successor);
	    }
	    
	    // System.out.print(">> visited:");
	    // System.out.println(current);
	    // System.out.print("**:");
	}
	
	return nodes;
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

    public void run(LowIrNode start){
	this.start = start;
	this.traverse((GraphNode)start);
    }
}