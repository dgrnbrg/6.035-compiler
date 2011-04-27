package decaf

class SpillVar extends TempVar {
  def static nextSpillID = 0;

  def static getNextID() {
    nextSpillId += 1;
    return nextSpillId;
  }

  SpillVar() {
    id = getNextID();
    type = TempVarType.SPILLVAR
  }
  
  String toString() {
    "SpillVar(I do not have an id yet!)"
  }
}

class SpillVarManager {
  def numSpillVars = 0;
  def lineup = [] // These are the spill vars for whom space will be allocated on the stack.
  def svLocMap = [:];
  def lowirLineup;
  def preservedRegSlots = null;

  SpillVarManager() {
    // Important: Here is where we create our registers
    preservedRegSlots = 
      ['rax', 'rbx', 'rcx', 'rdx', 
       'rsi', 'rdi', 'r8', 'r9', 
       'r10', 'r11', 'r12', 'r13', 'r14', 'r15'].collect { regName -> 
      new RegisterTempVar(regName)
    }
  }

  void addSpillVarToLineup(SpillVar sv) {
    assert sv
    assert lineup.contains(sv) == false
    lineup += [sv]
  }

  void PopulateLowIrLineup(LowIrNode start) {
    lowirLineup = new LinkedHashSet<SpillVar>();

    Traverser.eachNodeOf(start) { node -> 
      lowirLineup += [start.getDef()]
      lowirLineup += start.getUses()
    }
  }

  void ConstructFinalLineup() {
    svLocMap = [:]
    lineup = []

    // Now add spill var's for each register (for now we're just adding a slot 
    // for each of them to make life easier when preserving registers across 
    // method calls / callouts.
    assert preservedRegSlots
    perservedRegSlots.each { prs -> 
      svLocMap[(prs)] = lineup.size();
      lineup += [prs]
    }

    assert lowirLineup
    (lowirLineup as List).each { sv -> 
      svLocMap[(sv)] = lineup.size();
      lineup += [sv];
    }

    assert svLocMap.keySet().size() == lineup.size()
  }

  int getNumSpillVarsToAllocate() {
    assert false;
  }
}
