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

    def noArgs = new HiIrBuilder().Block(){
      method(name:'foo', returns: INT)
      MethodCall('foo')
      MethodCall('foo') { lit(true) }
    }
    errors.clear()
    noArgs.inOrderWalk(semCheck.methodCallArguments)
    assertEquals(1, errors.size())

    def twoArgs = new HiIrBuilder().Block(){
      method(name:'foo', returns: INT, takes:[BOOLEAN, INT])
      MethodCall('foo') { lit(true) }
      MethodCall('foo') { lit(true); lit(3) }
      MethodCall('foo') { lit(true); lit(true) }
      MethodCall('foo') { lit(true); lit(true); lit(3) }
      MethodCall('foo') { lit(true); lit(3); lit(false) }
    }
    errors.clear()
    twoArgs.inOrderWalk(semCheck.methodCallArguments)
    assertEquals(5, errors.size())
  }

  void testDeclareMethodLocation() {
    def ast = new ASTBuilder().build {
      Program(PROGRAM) {
        bar(METHOD_DECL) {
          'void'(TK_void)
          block(BLOCK)
        }
        main(METHOD_DECL) {
          'void'(TK_void)
          block(BLOCK) {
            foo(METHOD_CALL)
            bar(METHOD_CALL)
            baz(METHOD_CALL)
          }
        }
        foo(METHOD_DECL, line: 3) {
          'void'(TK_void)
          block(BLOCK)
        }
      }
    }
    def errors = []
    def stGen = new SymbolTableGenerator(errors: errors)
    def hiirGen = new HiIrGenerator(errors: errors)
    ast.inOrderWalk(stGen.c)
    assertEquals(0, errors.size())
    ast.inOrderWalk(hiirGen.c)
    assertEquals(2, errors.size())
  }

  void testDeclareVariableLocation() {
    def ast = new ASTBuilder().build {
      Program(PROGRAM) {
        main(METHOD_DECL) {
          'void'(TK_void)
          block(BLOCK) {
            'int'(VAR_DECL) {
              a(ID)
            }
            assignment(STATEMENT) {
              loc(LOCATION) {
                b(ID)
              }
              '='(PLAIN_ASSIGN_OP)
              '5'(INT_LITERAL)
            }
            assignment(STATEMENT) {
              loc(LOCATION) {
                a(ID)
              }
              '='(PLAIN_ASSIGN_OP)
              '7'(INT_LITERAL)
            }
          }
        }
      }
    }
    def errors = []
    def stGen = new SymbolTableGenerator(errors: errors)
    def hiirGen = new HiIrGenerator(errors: errors)
    ast.inOrderWalk(stGen.c)
    assertEquals(0, errors.size())
    ast.inOrderWalk(hiirGen.c)
    assertEquals(1, errors.size())
  }

  void testBreakContinueFor() {
    def bcfHiir = new HiIrBuilder().Block(){
      Break()
      Continue()
      ForLoop(index: 'i') {
        lit(1); lit(10); Block() {
          Break(); Continue()
        }
      }
      ForLoop(index: 'i') {
        lit(1); lit(10); Block() {
          Break(); Continue()
          ForLoop(index: 'i') {
            lit(1); lit(10); Block() {
              Break(); Continue()
            }
          }
          Break(); Continue()
        }
      }
      Break()
      Continue()
    }
    def errors = []
    def semCheck = new SemanticChecker(errors: errors)
    bcfHiir.inOrderWalk(semCheck.breakContinueFor)
    assertEquals(4, errors.size())
  }

  void testIfThenElseCondition() {
    def good = new HiIrBuilder().IfThenElse(){
      lit(true)
      Block()
    }
    def bad = new HiIrBuilder().IfThenElse(){
      lit(1)
      Block()
    }
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

    // 8
    badTypeCombos2.each { combo -> 
      [AND, OR].each { op -> 
        badConditions.add(new BinOp(op: op, left: combo[0], right: combo[1]));
      }
    }

    // 1
    badConditions.add(new BinOp(op: NOT, left: typicalIntLiteral))

    // 4
    [EQ, NEQ].each { op -> 
      badConditions.add(new BinOp(op: op, left: typicalIntLiteral, right: typicalBlnLiteral));
      badConditions.add(new BinOp(op: op, left: typicalBlnLiteral, right: typicalIntLiteral));
    }
    
    def numExpectedBadConds = 49;
    
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

    def myInt = new IntLiteral(value: 3);
    def myBln = new BooleanLiteral(value: false);
    
    def good = new HiIrBuilder().Block() {
      ForLoop(index: 'i') { lit(1); lit(10)
        Block()
      }
    }
    def bad = new HiIrBuilder().Block() {
      [[true,10],[1,false],[true,false]].each { argPair ->
        ForLoop(index: 'i') { lit(argPair[0]); lit(argPair[1])
          Block()
        }
      }
    }

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
    HiIrBuilder hb = new HiIrBuilder();

    // R = return
    Block g1 = hb.Block {
      Return() {
        lit(3)
      }
    }

    // R = { return }
    Block g2 = hb.Block {
      Block {
        Return() {
          lit(3)
        }
      }
    }

    // R = if R else R
    Block g3 = hb.Block {
      IfThenElse() {
        lit(true)
        Block() { Return() }
        Block() { Return() }
      }
    }

    // R = for R
    Block g4 = hb.Block {
      ForLoop(index: 'i') {
        lit(1); lit(10); 
        Block() { Return () }
      }
    }

    // R = R, NR
    Block g5 = hb.Block {
      Return();
      Block() { };
    }

    // R = NR, R
    Block g6 = hb.Block {
      Block() { };
      Return();
    }

    // NR = {}
    Block b1 = hb.Block {
    }

    // NR = if R
    Block b2 = hb.Block {
      IfThenElse() {
        lit(true)
        Block() { Return() }
      }
    }

    // NR = if NR
    Block b3 = hb.Block {
      IfThenElse() {
        lit(true)
        Block() { }
      }
    }

    // NR = if NR else NR
    Block b4 = hb.Block {
      IfThenElse() {
        lit(true)
        Block() { }
        Block() { }
      }
    }

    // NR = if R else NR
    Block b5 = hb.Block {
      IfThenElse() {
        lit(true)
        Block() { Return() }
        Block() { }
      }
    }

    // NR = if NR else R
    Block b6 = hb.Block {
      IfThenElse() {
        lit(true)
        Block() { }
        Block() { Return() }
      }
    }

    // NR = for NR 
    Block b7 = hb.Block {
      ForLoop(index: 'i') {
        lit(1); lit(10); 
        Block() { }
      }
    }

    def goodConditions = [g1, g2, g3, g4, g5, g6];
    def badConditions  = [b1, b2, b3, b4, b5, b6, b7];

    def goodErrors = []
    def badErrors = []
    def goodSemanticChecker = new SemanticChecker(errors: goodErrors)
    def badSemanticChecker  = new SemanticChecker(errors: badErrors)

    goodConditions.each {
      it.inOrderWalk(goodSemanticChecker.methodCallsThatAreExprReturnValue);
    }


    badConditions.each {
      it.inOrderWalk(badSemanticChecker.methodCallsThatAreExprReturnValue);
    }

    assertEquals(0, goodErrors.size());
    assertEquals(badConditions.size(),  badErrors.size());
  }
  
  void testReturnExprTypeMatchesMethodType() {
    def errors = [];
    HiIrBuilder hb = new HiIrBuilder();

    // Check for type INT, should give 2 errors
    Block b1 = hb.Block {
      method(name:'foo', returns: Type.INT)
      // here we return the right type!
      Return() { lit(3) }
      Return() { lit(true) }
      Return()
    }
    
    // Check for type BOOLEAN
    Block b2 = hb.Block {
      method(name:'bar', returns: Type.BOOLEAN)
      Return() { lit(3) }
      // here we return the right type!
      Return() { lit(true) }
      Return()
    }

    // Check for type VOID
    Block b3 = hb.Block {
      method(name:'bat', returns: Type.VOID)
      Return() { lit(3) }
      Return() { lit(true) }
      // here we return the right type!
      Return()
    }

    hb.methodSymTable['foo'].block = b1;
    hb.methodSymTable['bar'].block = b2;
    hb.methodSymTable['bat'].block = b3;
    
    // now we need to reach into b and create a methodSymTable
    def semanticChecker = new SemanticChecker(errors: errors, methodSymTable: hb.methodSymTable);
    
    [b1, b2, b3].each {
      it.inOrderWalk(semanticChecker.methodDeclTypeMatchesTypeOfReturnExpr);
    }

    assertEquals(6, errors.size());
  }
  
  void testAssignmentTypesAreCorrect() {
    def hiir = new HiIrBuilder().Block(){
      var(name:'a', type:INT_ARRAY, arraySize: 3)
      var(name:'b', type:BOOLEAN)
      Assignment(line: 0) { Location('a'){lit(0)}; lit(3) }
      Assignment(line: 1) { Location('a'); lit(3) }
      Assignment(line: 2) { Location('a'); lit(true) }
      Assignment(line: 3) { Location('b'); lit(true) }
      Assignment(line: 4) { Location('b'); lit(3) }
    }
    def errors = []
    def semCheck = new SemanticChecker(errors: errors)
    hiir.inOrderWalk(semCheck.assignmentTypesAreCorrect)
    assertEquals(3,errors.size())
  }

  void testArrayIndicesAreInts() {
    def hiir = new HiIrBuilder().Block(){
      var(name:'a', type:INT_ARRAY, arraySize: 3)
      var(name:'b', type:BOOLEAN)
      Assignment() { Location('a'){lit(true)}; lit(2) }
      Assignment() { Location('a'){lit(1)}; lit(2) }
      Assignment() { Location('b'){lit(1)}; lit(true) }
    }
    def errors = []
    def semCheck = new SemanticChecker(errors: errors)
    hiir.inOrderWalk(semCheck.arrayIndicesAreInts)
    assertEquals(2,errors.size())
  }
}
