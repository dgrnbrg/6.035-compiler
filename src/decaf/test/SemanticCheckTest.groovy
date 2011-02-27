package decaf.test
import decaf.*
import static decaf.BinOpType.*
import static decaf.Type.*
import static decaf.DecafParserTokenTypes.*

class SemanticCheckTest extends GroovyTestCase {
  def ASTBuilder astb = new ASTBuilder()

  void testMethodCallArguments() {
    def prog1 = astb.compile {
      Program(PROGRAM) {
        bar(METHOD_DECL) {
          'void'(TK_void)
          'int'(VAR_DECL) {
            a(ID)
          }
          block(BLOCK)
        }
        main(METHOD_DECL) {
          'void'(TK_void)
          block(BLOCK) {
            bar(METHOD_CALL) {
              '1'(INT_LITERAL)
              '2'(INT_LITERAL)
            }
          }
        }
      }
    }

    assertTrue(prog1 instanceof HiIrGenerator)
    def errors = []
    def semCheck = new SemanticChecker(errors: errors)
    prog1.methods['main'].inOrderWalk(semCheck.methodCallArguments)
    assertEquals(1, errors.size())
  }

  void testIfThenElseCondition() {
    def good = new IfThenElse(condition: new BooleanLiteral(value:true), thenBlock: new Block())
    def bad = new IfThenElse(condition: new IntLiteral(value:1), thenBlock: new Block())
    def errors = []
    def semCheck = new SemanticChecker(errors: errors)
    good.inOrderWalk(semCheck.ifThenElseConditionCheck)
    bad.inOrderWalk(semCheck.ifThenElseConditionCheck)
    assertEquals(1, errors.size())
  }

  void testBinOpOperands() {
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

    def badTypeCombos1 = [[typicalIntLiteral, typicalBlnLiteral], 
                         [typicalBlnLiteral, typicalIntLiteral], 
                         [typicalBlnLiteral, typicalBlnLiteral]];

    // 36
    badTypeCombos1.each { combo -> 
      [ADD, SUB, MUL, DIV, MOD, LT, GT, LTE, GTE].each { arithOp -> 
          badConditions.add(new BinOp(op: arithOp, left: combo[0], right: combo[1]))
        }
      }

    def badTypeCombos2 = [[typicalIntLiteral, typicalBlnLiteral], 
                         [typicalBlnLiteral, typicalIntLiteral], 
                         [typicalIntLiteral, typicalIntLiteral]];

    // 12
    badTypeCombos2.each { combo -> 
      [AND, OR, NOT].each { op -> 
        badConditions.add(new BinOp(op: op, left: combo[0], right: combo[1]));
      }
    }

    // 4
    [EQ, NEQ].each { op -> 
      badConditions.add(new BinOp(op: op, left: typicalIntLiteral, right: typicalBlnLiteral));
      badConditions.add(new BinOp(op: op, left: typicalBlnLiteral, right: typicalIntLiteral));
    }
    
    def numExpectedBadConds = 52;
    
    goodConditions.each { 
      it.inOrderWalk(goodSemanticChecker.binOpOperands)
    }
    
    badConditions.each { 
      it.inOrderWalk(badSemanticChecker.binOpOperands)
    }

    assertEquals(numExpectedBadConds, badErrors.size());
    assertEquals(0, goodErrors.size());
  }

  void testForLoopInitEndExprTypeInt() {
    def errors = [];
    def semanticChecker = new SemanticChecker(errors: errors);

    // david if you are reading this say hi!
    def myInt = new IntLiteral(value: 3);
    def myBln = new BooleanLiteral(value: false);
    
    def good = new ForLoop(low: myInt, high: myInt);
    def bad = [new ForLoop(low: myInt, high: myBln),
                new ForLoop(low: myBln, high: myInt),
                new ForLoop(low: myBln, high: myBln)];

    good.inOrderWalk(semanticChecker.forLoopInitEndExprTypeInt);
    bad.each { 
      it.inOrderWalk(semanticChecker.forLoopInitEndExprTypeInt);
    }
    
    // should get a total of 4 errors
    assertEquals(4, errors.size());
  }

  void testArrayDeclArraySizeGreaterZero() {
    def ASTBuilder astb1 = new ASTBuilder()
    def ASTBuilder astb2 = new ASTBuilder()
    
    def prog1 = astb.compile {
      'Program'(PROGRAM) {
        'int'(VAR_DECL) {
          'b'(ARRAY_DECL) {
            '1'(INT_LITERAL)
          }
        }
      }
    }
    
    def prog2 = astb.compile {
      'Program'(PROGRAM) {
        'int'(VAR_DECL) {
          'b'(ARRAY_DECL) {
            '0'(INT_LITERAL)
          }
        }
      }
    }
    
    assertTrue(prog1 instanceof HiIrGenerator)
    assertTrue(prog2 instanceof List)
    assertEquals(1, prog2.size())
  }

  void testMethodMustReturn() {
    def goodErrors = [];
    def badErrors = [];
    def goodSemanticChecker = new SemanticChecker(errors: goodErrors)
    def badSemanticChecker  = new SemanticChecker(errors: badErrors)

    def goodConditions = [];
    def badConditions = [];

    def ASTBuilder astb1 = new ASTBuilder()

    astb1.compile {
      'Program'(PROGRAM) {
        // bar 1 has no return statement!
        'bar1'(METHOD_DECL) {
          'void'(TK_void)
          block(BLOCK) {
          }
        }
        'bar2'(METHOD_DECL) {
          'void'(TK_void)
          
      }
    }
            
          
  }
}
