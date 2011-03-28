package decaf;
import decaf.graph.*;
import java.util.*;
import org.apache.commons.collections.iterators.IteratorChain;

enum DefinitionType {
    VARIABLE,
    SCALAR
	};

class Definition implements Comparable{
    protected int value;
    protected TempVar tempVar;
    private DefinitionType defType;
    
    Definition(int value){
	this.value = value;
	this.defType = DefinitionType.SCALAR;
    }
    
    Definition(TempVar tempVar){
	this.tempVar = tempVar;
	this.defType = DefinitionType.VARIABLE;
    }

    DefinitionType getDefType(){
	return this.defType;
    }

    public int compareTo(Object o){
	if(o instanceof Definition){
	    if(((Definition)o).defType == DefinitionType.VARIABLE &&
	       this.defType == DefinitionType.VARIABLE){
		return ((Comparable)o).compareTo(this.tempVar);
	    }
	    else if(((Definition)o).defType == DefinitionType.SCALAR &&
		    this.defType == DefinitionType.SCALAR){
		int oValue = ((Definition)o).value;
			
		if(oValue > this.value){
		    return -1;
		}
		else if(oValue == this.value){
		    return 0;
		}
		else {
		    return 1;
		}
	    }
	    else {
		if(this.defType == DefinitionType.SCALAR)
		    return -1;
		else
		    return 1;
	    }
	     
	} else {
	    return -1;
	}
    }
}

class ReachingDefinitions extends Analysis<TreeMap<TempVar,TreeSet<Definition>>>{
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> joinOp(Set<TreeMap<TempVar,TreeSet<Definition>>> inputs){
	TreeMap<TempVar,TreeSet<Definition>> output = new TreeMap<TempVar,TreeSet<Definition>>();

	for(TreeMap<TempVar,TreeSet<Definition>> input : inputs){
	    for(TempVar variable : input.keySet()){
		TreeSet<Definition> current = null;
		
		if(output.containsKey(variable)){
		    current = (TreeSet)output.get(variable).clone();
		} else {
		    current = new TreeSet<Definition>();
		}
		
		current.addAll(input.get(variable));
		output.put(variable, current);
	    }
	}
	return output;
    }

    private TreeMap<TempVar,TreeSet<Definition>> kill(TreeMap<TempVar,TreeSet<Definition>> definitions, TempVar variable){
	if(definitions.containsKey(variable)){
	    definitions.remove(variable);
	}
	return definitions;
    }
    
    private TreeMap<TempVar,TreeSet<Definition>> generate(TreeMap<TempVar,TreeSet<Definition>> definitions, TempVar variable, Definition definition){
	assert(!definitions.containsKey(variable));
	TreeSet<Definition> singleton = new TreeSet<Definition>();
	singleton.add(definition);

	definitions.put(variable, singleton);
	return definitions;
    }
    
    // @Override
    // public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrValueNode node, TreeMap<TempVar,TreeSet<Definition>> input){
    // 	return input;
    // }
    
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrIntLiteral node, TreeMap<TempVar,TreeSet<Definition>> input){
	return input;
    }
    
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrMov node, TreeMap<TempVar,TreeSet<Definition>> input){
	TreeMap<TempVar,TreeSet<Definition>> definitions = (TreeMap<TempVar,TreeSet<Definition>>)input.clone();
	TempVar variable = node.dst;
	
	return this.generate(this.kill(definitions, variable), variable, new Definition(node.src));
    }
    
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrBinOp node, TreeMap<TempVar,TreeSet<Definition>> input){
	return input;
    }
    
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrReturn node, TreeMap<TempVar,TreeSet<Definition>> input){
	return input;
    }
    
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrStringLiteral node, TreeMap<TempVar,TreeSet<Definition>> input){
	return input;
    }
    
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrMethodCall  node, TreeMap<TempVar,TreeSet<Definition>> input){
	return input;
    }
    
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrCallOut  node, TreeMap<TempVar,TreeSet<Definition>> input){
	return input;
    }
    
    @Override
    public TreeMap<TempVar,TreeSet<Definition>> transfer(LowIrCondJump  node, TreeMap<TempVar,TreeSet<Definition>> input){
	return input;
    }

    @Override
    public TreeMap<TempVar,TreeSet<Definition>> emptyState(){
	return (new TreeMap<TempVar,TreeSet<Definition>>());
    }
}