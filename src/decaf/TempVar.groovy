package decaf

class TempVar {
  int id //unique per temp var within a method
  TempVarType type //LOCAL, GLOBAL, etc
  String globalName //if it's a global, this will have the string of the global it had space allocated in
  TempVar arrayIndexTmpVar //if it's an ARRAY type, this is the tempvar that contains its index
  VariableDescriptor desc //if it's tied to a variable, this is the descriptor for that variable

  String toString() {
    "TempVar($id, $type)"
  }
}

enum TempVarType {
  LOCAL,
  PARAM,
  GLOBAL,
  ARRAY //always static
}

//Within a methoddesc, tracks temp var usage and contains the code to allocate tempvars on the hiir and symboltables
class TempVarFactory {
  def MethodDescriptor methodDesc // this is the methodDesciptor for the program fragment we're making temps for

  def tmpVarId = 0  //how many tempvars have been allocated locally?

  int nextId() {
    return tmpVarId++
  }

  TempVarFactory(MethodDescriptor desc) {
    methodDesc = desc
  }

  //put tmpVar on every hiir node and params
  void decorateMethodDesc() {
    methodDesc.block.inOrderWalk { cur ->
      if(cur instanceof Expr ||
        cur instanceof StringLiteral){
        // Allocates TempVar()s for temporary nodes
        try {
          declVar('tmpVar', createLocalTemp())
        } catch (MissingPropertyException e) {}
      } else if (cur instanceof Block || cur instanceof ForLoop) {
        // Allocates TempVar()s for all declared variables
        cur.symTable.@map.each { k, v ->
          v.tmpVar = createLocalTemp()
          v.tmpVar.desc = v
        }
      }
      walk()
    }
    methodDesc.params.eachWithIndex {param, index ->
      param.tmpVar = new TempVar(id: index, type: TempVarType.PARAM)
    }
  }

  TempVar createLocalTemp() {
    return new TempVar(id: nextId(), type: TempVarType.LOCAL)
  }

  //TODO: get this working (it's broken now)
  static List generateForGlobals(SymbolTable globalSymbolTable) {
    assert globalSymbolTable.parent == null
    def bssList = []
    globalSymbolTable.@map.each { name, desc ->
      name += '_globalvar'
      desc.tmpVar = new TempVar(TempVarType.GLOBAL)
      desc.tmpVar.globalName = name
      desc.tmpVar.desc = desc
      def s = desc.arraySize
      if (s == null) s = 1
      bssList << ".comm $name ${8*s}"
    }
  }
}
