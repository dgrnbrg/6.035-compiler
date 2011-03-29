package decaf;
import decaf.graph.*;
import java.util.*;
import org.apache.commons.collections.iterators.IteratorChain;

abstract class DataFlowAnalysis{
    // Important remark regarding forward vs backwards analysis:
    // ---------------------------------------------------------
    // The difference between these two lies in your transfer
    // function. In a forward analysis, your input is the output
    // of all of the *predecessors* to your current node. In a
    // backwards analysis, your input is the output of all of the
    // *successors* of your current node.
    
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
	    if(this.transfer(node, this.join(node)) == true){
		for(GraphNode sucessor : node.getSuccessors()){
		    workQueue.offer(successor);
		}
	    }
	}
    }
}

