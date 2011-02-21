package decaf

import static decaf.DecafParserTokenTypes.*
import decaf.SymbolTable

public class SymbolTableGenerator {

  // This is the closure that is passed around
  Closure c = { AST cur -> 
    // look at 
    println "My value is: ${cur.getText()}, my hashCode is ${cur.hashCode()} and my type is: ${cur.getType()}";

    declVar('methodSymTable', cur.getType() == PROGRAM ? new SymbolTable() : parent.methodSymTable)
    declVar('symTable', cur.getType() == PROGRAM ? new SymbolTable() : parent.symTable)
    if (cur.getType() == VAR_DECL) {
      declVar ('vars', [])
    }
    walk()

    switch(cur.getType()) {
    case PROGRAM: 
      println "PROGRAM LEVEL $symTable"
      break
    case METHOD_DECL: 
      assert symTable
      break
    case VAR_DECL:
      println "VAR_DECL LEVEL parent's $symTable"
      assert cur.getText() == 'int' || cur.getText() == 'boolean'
      def scalarType = cur.getText() == 'int' ? Type.INT : Type.BOOLEAN
      def arrayType = cur.getText() == 'int' ? Type.INT_ARRAY : Type.BOOLEAN_ARRAY
      vars.each { VariableDescriptor desc ->
        desc.type = arraySize ? arrayType : scalarType
        symTable[desc.name] = desc
      }
      break
    case ID:
      // assert that parent.myType is either a 
      println "ID LEVEL $symTable"
      //assert symTable
      if (parent.getType() == VAR_DECL) {
        parent.vars << new VariableDescriptor(name: cur.getText())
      }
      break
    case ARRAY_DECL:      
      println "I am an array!"
      declVar('arraySize')
      assert arraySize != null
      parent.vars << new VariableDescriptor(name: cur.getText(), arraySize: arraySize)
      break
    case INT_LITERAL: 
      if(parent.getType() == ARRAY_DECL) {
        parent.arraySize = cur.getText() as int
        println "The array size is set to ${parent.walkerDelegate.arraySize}"
      }
      break
    default:
      assert false
      break
    }
  }
}

enum Type {
  INT, BOOLEAN, INT_ARRAY, BOOLEAN_ARRAY, VOID
}

public class VariableDescriptor {
  String name
  Type type
  def arraySize
}

public class MethodDescriptor {
  Type returnType
  Block block
}
