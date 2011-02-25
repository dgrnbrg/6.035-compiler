package decaf.test
import decaf.*
import static decaf.BinOpType.*
import static decaf.Type.*

class SemanticCheckTest extends GroovyTestCase {
  // void testGetTypeIntLiteral(){
  //   def intLiteral = new IntLiteral(value:1)
  //   assertEquals(Type.INT, SemanticChecker.getExprType(intLiteral))
  // }
  // void testGetTypeBooleanLiteral(){
  //   def booleanLiteral = new BooleanLiteral(value:true)
  //   assertEquals(Type.BOOLEAN, SemanticChecker.getExprType(booleanLiteral))
  // }
  

  void testGetExprType() {
    def goodErrors = [];
    def badErrors = [];
    def goodSemanticChecker = new SemanticChecker(errors: goodErrors)
    def badSemanticChecker  = new SemanticChecker(errors: badErrors)
    
    def goodConditions = [];
    def badConditions = [];

    def typicalBlnLiteral = new BooleanLiteral(value: true);
    def typicalIntLiteral = new IntLiteral(value: 3);

    // test the good conditions
    goodConditions.addAll([LT, GT, LTE, GTE].collect { 
      new BinOp(op: it, left: typicalIntLiteral, right: typicalIntLiteral)
    });

    goodConditions.addAll([EQ, NEQ].collect { 
      new BinOp(op: it, left: typicalIntLiteral, right: typicalIntLiteral)
    });

    goodConditions.addAll([EQ, NEQ, AND, OR, NOT].collect { 
      new BinOp(op: it, left: typicalBlnLiteral, right: typicalBlnLiteral)
    });

    goodConditions.add(new MethodCall(descriptor: new MethodDescriptor(returnType: BOOLEAN)));

    goodConditions.add(new Location(descriptor: new VariableDescriptor(type: BOOLEAN)));
    goodConditions.add(new Location(descriptor: new VariableDescriptor(type: BOOLEAN_ARRAY), indexExpr: typicalIntLiteral));

    goodConditions.add(typicalBlnLiteral);

    def badTypeCombos1 = [[typicalIntLiteral, typicalBlnLiteral], 
                         [typicalBlnLiteral, typicalIntLiteral], 
                         [typicalBlnLiteral, typicalBlnLiteral]];

    // 36 + 27 = 63
    badTypeCombos1.each { combo -> 
      [ADD, SUB, MUL, DIV, MOD, LT, GT, LTE, GTE].each { arithOp -> 
          badConditions.add(new BinOp(op: arithOp, left: combo[0], right: combo[1]))
        }
      }

    def badTypeCombos2 = [[typicalIntLiteral, typicalBlnLiteral], 
                         [typicalBlnLiteral, typicalIntLiteral], 
                         [typicalIntLiteral, typicalIntLiteral]];

    // 12 + 9 = 21
    badTypeCombos2.each { combo -> 
      [AND, OR, NOT].each { op -> 
        badConditions.add(new BinOp(op: op, left: combo[0], right: combo[1]));
      }
    }

    // 4 + 4 = 8
    [EQ, NEQ].each { op -> 
      badConditions.add(new BinOp(op: op, left: typicalIntLiteral, right: typicalBlnLiteral));
      badConditions.add(new BinOp(op: op, left: typicalBlnLiteral, right: typicalIntLiteral));
    }
    
    // 3
    badConditions.add(new MethodCall(descriptor: new MethodDescriptor(returnType: INT)));
    badConditions.add(new MethodCall(descriptor: new MethodDescriptor(returnType: VOID)));
    badConditions.add(new CallOut());
    
    def numExpectedBadConds = 95;
    
    goodConditions.each { 
      def blah = new IfThenElse(condition: it, thenBlock: new Block());
      blah.inOrderWalk(goodSemanticChecker.ifThenElseConditionCheck);
    }
    
    badConditions.each { 
      def blah = new IfThenElse(condition: it, thenBlock: new Block());
      blah.inOrderWalk(badSemanticChecker.ifThenElseConditionCheck);
    }

    assertEquals(numExpectedBadConds, badErrors.size());
    assertEquals(0, goodErrors.size());
  }

}
