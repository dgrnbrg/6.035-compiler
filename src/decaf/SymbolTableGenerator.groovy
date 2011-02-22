package decaf

import static decaf.DecafParserTokenTypes.*
import decaf.SymbolTable

public class SymbolTableGenerator {

  // This is the closure that is passed around
  Closure c = { AST cur -> 

    //pull method symbol table unless PROGRAM node
    declVar('methodSymTable',
      cur.getType() == PROGRAM ? new SymbolTable() : parent.methodSymTable)

    //where to get SymbolTable from
    switch(cur.getType()) {
    case PROGRAM:
      declVar('symTable', new SymbolTable())
      break

    case METHOD_DECL:
    case BLOCK:
    case TK_for:
      declVar('symTable', new SymbolTable(parent.symTable))
      break

    default:
      declVar('symTable', parent.symTable)
      break       
    }

    //declare helper variables
    if (cur.getType() == ARRAY_DECL) declVar('arraySize')
    if (cur.getType() == VAR_DECL) declVar('vars',[])
    if (cur.getType() == METHOD_DECL) {
      declVar('methodDesc', new MethodDescriptor(name:cur.getText()))
      methodSymTable[cur.getText()] = methodDesc
    }

    walk()

    switch(cur.getType()) {
    case VAR_DECL:
      assert cur.getText() == 'int' || cur.getText() == 'boolean'
      def scalarType = cur.getText() == 'int' ? Type.INT : Type.BOOLEAN
      def arrayType = cur.getText() == 'int' ? Type.INT_ARRAY : Type.BOOLEAN_ARRAY
      vars.each { VariableDescriptor desc ->
        desc.type = desc.arraySize != null ? arrayType : scalarType
        symTable[desc.name] = desc
      }
      if (parent.getType() == METHOD_DECL) {
        parent.methodDesc.params = vars
      }
      break

    case ID:
      if (parent.getType() == VAR_DECL) {
        parent.vars << new VariableDescriptor(name: cur.getText())
      } else if (parent.getType() == TK_for) {
        symTable[cur.getText()] =
          new VariableDescriptor(name: cur.getText(), type: Type.INT)
      }
      break

    case ARRAY_DECL:      
      assert arraySize != null
      parent.vars << new VariableDescriptor(
        name: cur.getText(),
        arraySize: arraySize
      )
      break

    case INT_LITERAL: 
      if(parent.getType() == ARRAY_DECL) {
        parent.arraySize = (cur.getText() as int)
      }
      break

    case TK_int:
    case TK_boolean:
    case TK_void:
      if (parent.getType() == METHOD_DECL) {
        parent.methodDesc.returnType = [
          (TK_int) : Type.INT,
          (TK_boolean) : Type.BOOLEAN,
          (TK_void) : Type.VOID
        ][cur.getType()]
      }
      break
    }
  }
}
