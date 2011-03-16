package decaf

class TempVar {
  int tempVarNumber
  static tempVarCounter = 0
  TempVarType type = TempVarType.LOCAL
  String globalName
  TempVar arrayIndexTmpVar
  VariableDescriptor desc

  // Constructor
  TempVar(){
    tempVarNumber = TempVar.tempVarCounter
    tempVarCounter++
  }
  TempVar(TempVarType type) {
    this.type = type
  }
  int getId(){
    return this.tempVarNumber
  }
  static exitFunction(){
    TempVar.tempVarCounter = 0
  }

  String toString() {
    "TempVar($tempVarNumber, $type, globalName: $globalName)"
  }
}

enum TempVarType {
  LOCAL,
  PARAM,
  GLOBAL,
  ARRAY //always static
}

class TempVarGenerator {
  static void generateForMethod(MethodDescriptor methodDesc) {
    def tmpNum = methodDesc.params.size()
    methodDesc.block.inOrderWalk { cur ->
      if(cur instanceof Expr ||
        cur instanceof StringLiteral){
        // Allocates TempVar()s for temporary nodes
        try {
          declVar('tmpVar', new TempVar())
          tmpNum++
        } catch (MissingPropertyException e) {}
      } else if (cur instanceof Block || cur instanceof ForLoop) {
        // Allocates TempVar()s for all declared variables
        cur.symTable.@map.each { k, v ->
          tmpNum++
          v.tmpVar = new TempVar(desc: v)
        }
        if (cur instanceof ForLoop) {
          for (int i = 0; i < cur.extras.length; i++) {
            cur.extras[i] = new TempVar()
          }
        }
      }
      walk()
    }
    methodDesc.maxTmpVars = tmpNum
    TempVar.exitFunction()
    methodDesc.params.eachWithIndex {param, index ->
      param.tmpVar = new TempVar(TempVarType.PARAM)
      param.tmpVar.tempVarNumber = index
    }
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
