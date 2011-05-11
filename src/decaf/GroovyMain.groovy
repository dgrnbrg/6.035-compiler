package decaf
import antlr.*
import groovy.util.*
import decaf.RegisterAllocation.*
import org.apache.commons.cli.*
import decaf.graph.*
import static decaf.DecafScannerTokenTypes.*
import antlr.collections.AST as AntlrAST
import decaf.test.HiIrBuilder
import decaf.optimizations.*

class LowIrDotTraverser extends Traverser {
  def out

  void visitNode(GraphNode cur) {
    // set nodeColor to "" if you don't want to render colors
    def nodeColor = ", style=filled, color=\"${TraceGraph.getColor(cur)}\""
//    out.println("${cur.hashCode()} [label=\"$cur $cur.label\\n${cur.anno['regalloc-liveness']}\\n${cur.anno['insert']}\\n${cur.anno['delete']}\"$nodeColor]")
    out.println("${cur.hashCode()} [label=\"$cur $cur.label\\n${cur.anno['regalloc-liveness']}\"$nodeColor]")
  }
  void link(GraphNode src, GraphNode dst) {
    out.println("${src.hashCode()} -> ${dst.hashCode()}")
  }
}

public class GroovyMain {
  static Closure makeGraph(PrintStream out, root = null) {
    return { cur ->
      out.println("${cur.hashCode()} [label=\"$cur\"]")
      walk()
      if (cur.parent != null)
        out.println("${parent.hashCode()} -> ${cur.hashCode()}")
      else if (root)
        out.println("${root.hashCode()} -> ${cur.hashCode()}")
    }
  }

  public static void main(String[] args) {
    new GroovyMain(args)
  }

  def argparser
  def file
  def inputStream
  def exitHooks = []
  def errors = []
  //used for storing a failure exception when called from code
  def failException
  static debug = false

  static GroovyMain runMain(String target, String decafProgram, args = [:]) {
    def bytes = decafProgram.getBytes()
    def is = new ByteArrayInputStream(bytes)
    def main = new GroovyMain(is)
    main.argparser = args
    try {
      main."$target"()
    } catch (Exception e) {
      main.failException = e
    }
    return main
  }

  GroovyMain(InputStream is) {
    inputStream = is
  }

  GroovyMain(args) {
    argparser = new ArgParser()
    try {
      argparser.parse(args as List)
    } catch (e) {
      System.err.println("$e")
      System.exit(1)
    }
    if (argparser['other'].size != 1) {
      println 'You must pass exactly one file to be compiled.'
      System.exit(1)
    }
    file = argparser['other'][0]
    debug = argparser['debug'] != null
    inputStream = new File(file).newDataInputStream()

    int exitCode = 0
    exitHooks << { ->
      errors*.file = file
      errors.each { println it; exitCode = 1 }
    }

    try {
      def target = argparser['target']
      if (target == null) target = 'codegen'
      depends(this."$target")
    } catch (FatalException e) {
      println e
      exitCode = e.code
    } catch (Throwable e) {
      def skipPrefixes = ['org.codehaus','sun.reflect','java.lang.reflect','groovy.lang.Meta']
      def st = e.getStackTrace().findAll { traceElement ->
        !skipPrefixes.any { prefix ->
          traceElement.getClassName().startsWith(prefix)
        }
      }
      println e
      st.each {
        def location = it.getFileName() != null ? "${it.getFileName()}:${it.getLineNumber()}" : 'Unknown'
        println "  at ${it.getClassName()}.${it.getMethodName()}($location)"
      }
      exitCode = 1
    } finally {
      exitHooks.each{ it() }
    }
    System.exit(exitCode)
  }

  def completedTargets = []
  def runningTargets = []
  def depends(target) {
    if (runningTargets.contains(target)) {
      println "There is a cyclic dependency in the targets, and I'll bet you it's your fault..."
      System.exit(22)
    }
    if (!completedTargets.contains(target)) {
      runningTargets << target
      target()
      runningTargets.pop()
      completedTargets << target
    }
  }

  def scan = {->
    def lexer = new LexerIterator(
      lexer: new DecafScanner(inputStream),
      onError: {e, l -> println "$file $e"; l.consume() })

    lexer.each{ token ->
      def typeRename = [(ID): ' IDENTIFIER', (INT_LITERAL): ' INTLITERAL',
        (CHAR_LITERAL): ' CHARLITERAL', (STRING_LITERAL): ' STRINGLITERAL',
        (TK_true): ' BOOLEANLITERAL', (TK_false): ' BOOLEANLITERAL']
      def text = token.text
      if (token.type == CHAR_LITERAL) {
        text = "'$text'"
      } else if (token.type == STRING_LITERAL) {
        text = "\"$text\""
      }
      println "$token.line${typeRename[token.type] ?: ''} $text"
    }
  }

  def ast
  def parse = {->
    try {
      def lexer = new DecafScanner(inputStream)
      def parser = new DecafParser(lexer)
      ASTFactory factory = new ASTFactory()
      factory.setASTNodeClass(CommonASTWithLines.class)
      parser.setASTFactory(factory)
      parser.program()
      ast = AST.fromAntlrAST(parser.getAST())
    } catch (RecognitionException e) {
      e.printStackTrace()
      System.exit(1)
    }
  }

  def dotOut
  def setupDot = {->
    def graphFile, extension = 'pdf'
    if (argparser['o']) {
      graphFile = argparser['o']
    if (graphFile.contains('.'))
      extension = graphFile.substring(graphFile.lastIndexOf('.') + 1, graphFile.length())
    } else {
      graphFile = file
      if (graphFile.contains('.'))
        graphFile = graphFile.substring(0, graphFile.lastIndexOf('.'))
      graphFile = graphFile + '.' + argparser['target'] + '.' + extension
      println "Writing output to $graphFile"
    }
    assert graphFile

    def dotCommand = "dot -T$extension -o $graphFile"
    try {
      Process dot = dotCommand.execute()
      dotOut = new PrintStream(dot.outputStream)
      dot.consumeProcessErrorStream(System.err)
      exitHooks << { dotOut.close() }
    } catch (IOException e) {
      println "Dot command: $dotCommand"
      println "Did you install graphviz?"
      e.printStackTrace()
      System.exit(1)
    }
  }

  def antlrast = {->
    depends(parse)
    depends(setupDot)
    dotOut.println('digraph g {')
    ast.inOrderWalk(makeGraph(dotOut))
    dotOut.println('}')
  }

  def opts = new LinkedHashSet()
  def decideOptimizations = {->
    if ('cse' in argparser['opt']) {
      opts += ['ssa', 'cse']
    }
    if ('ssa' in argparser['opt']) {
      opts << 'ssa'
    }
    if ('cp' in argparser['opt']) {
      opts += ['ssa', 'cp']
    }
    if ('dce' in argparser['opt']) {
      opts += ['ssa', 'dce']
    }
    if ('dse' in argparser['opt']) {
      opts += ['ssa', 'dse']
    }
    if ('pre' in argparser['opt']) {
      opts += ['ssa', 'pre']
    }
    if ('sccp' in argparser['opt']) {
      opts += ['ssa', 'sccp']
    }
    if ('iva' in argparser['opt']) {
      opts += ['ssa', 'iva']
    }
    if ('regalloc' in argparser['opt']) {
      opts += ['ssa', 'regalloc']
    }
    if ('cc' in argparser['opt']) {
      opts += ['ssa', 'cc']
    }
    if ('inline' in argparser['opt'] || 'all' in argparser['opt']) {
      lowirGen.inliningThreshold = 50
    } else {
      lowirGen.inliningThreshold = 0
    }
    if ('all' in argparser['opt']) {
      opts += ['ssa', 'dce', 'pre', 'cp', 'sccp', 'dse', 'cc', 'regalloc', 'iva']
    }
  }

  def symTableGenerator = new SymbolTableGenerator(errors: errors)

  //todo: test that dot is closed even if symtable not generated
  //ie test that the finally block in the entry point is executed when we system.exit
  def genSymTable = {->
    depends(parse)
    ast.inOrderWalk(symTableGenerator.c)
    if (errors != []) throw new FatalException(code: 1)
  }

  def symtable = {->
    depends(genSymTable)
    depends(setupDot)
    dotOut.println('digraph g {')
    ast.symTable.inOrderWalk(makeGraph(dotOut))
    ast.methodSymTable.inOrderWalk(makeGraph(dotOut))
    dotOut.println('}')
  }

  def hiirGenerator = new HiIrGenerator(errors: errors)
  def methodDescs

  def genHiIr = {->
    depends(genSymTable)

    // Force assert off here for RegAlloc Testing Purposes
    //if(argparser['assertEnabled'])
    //  argparser['assertEnabled'] = null

    if(argparser['assertEnabled'] != null) {
      ast.methodSymTable["assert"] = AssertFn.getAssertMethodDesc()
    }

    ast.inOrderWalk(hiirGenerator.c)
    // Here is where the assert function should be added to the 
    // method symbol table.
    //
    methodDescs = [] + ast.methodSymTable.values()
    if (errors != []) throw new FatalException(code: 1)
  }

  def hiir = {->
    depends(genHiIr)
    depends(setupDot)
    dotOut.println('digraph g {')
    hiirGenerator.methods.each {k, v ->
      dotOut.println("${k.hashCode()} [label=\"$k\"]")
      v.inOrderWalk(makeGraph(dotOut, k))
    }
    dotOut.println '}'
  }

  def inter = {->
    depends(genHiIr)
    def checker = new SemanticChecker(errors: errors, methodSymTable: ast.methodSymTable)
    methodDescs.each { MethodDescriptor desc ->
      def methodHiIr = desc.block
      assert methodHiIr != null
      //ensure that all HiIr nodes have their fileInfo
      methodHiIr.inOrderWalk{
        assert it.fileInfo != null
        walk()
      }
/*
      checker.checks.each { check ->
        assert check != null
        methodHiIr.inOrderWalk(check)
      }
*/
      methodHiIr.inOrderWalk(checker.arrayIndicesAreInts)
      if (errors != []) throw new FatalException(code: 1)
      methodHiIr.inOrderWalk(checker.hyperblast)
    }
    
    checker.mainMethodCorrect()
    
    if (errors != []) throw new FatalException(code: 1)
  }

  def stepOutOfSSA = { -> 
    methodDescs.each { methodDesc -> 
      if(('regalloc' in opts) == false) {
        println "stepping out of SSA form."
        SSAComputer.destroyAllMyBeautifulHardWork(methodDesc.lowir)
      }
    }
  }

  def lowir = {->
    depends(setupDot)
    depends(genLowIr)

    dotOut.println('digraph g {')
    methodDescs.each { methodDesc ->
      SSAComputer.destroyAllMyBeautifulHardWork(methodDesc.lowir)
      TraceGraph.calculateTraces(methodDesc.lowir);
      new LowIrDotTraverser(out: dotOut).traverse(methodDesc.lowir)
    }
    dotOut.println '}'
  }

  def lowirGen = new LowIrGenerator()

  def genLowIr = {->
    depends(inter)
    depends(decideOptimizations) //cse, ssa, all, cp, dce
    depends(genTmpVars)
    methodDescs.each { MethodDescriptor methodDesc ->
      methodDesc.lowir = lowirGen.destruct(methodDesc).begin
    }
    println "Optimizations are: $opts"
    
    methodDescs.each { MethodDescriptor methodDesc ->
      if ('ssa' in opts)
        new SSAComputer().compute(methodDesc)
//        new CommonSubexpressionElimination().run(methodDesc)
      if ('sccp' in opts)
        new SparseConditionalConstantPropagation().run(methodDesc)
      if ('cp' in opts)
        new CopyPropagation().propagate(methodDesc.lowir)
      if ('cse' in opts)
        new LocalCSE().run(methodDesc)
      if ('dce' in opts)
        new AggressiveDCE().run(methodDesc.lowir)
      if ('dse' in opts)
        new DeadStoreElimination().run(methodDesc.lowir)
      if ('pre' in opts) {
        def repeats = 0
        def stillGoing = true
        while (repeats < 6 && stillGoing) {
          def lcm = new LazyCodeMotion()
          lcm.run(methodDesc)
          new CopyPropagation().propagate(methodDesc.lowir)
          new AggressiveDCE().run(methodDesc.lowir)
          stillGoing = lcm.insertCnt != lcm.deleteCnt
          repeats++
        }
        new DeadStoreElimination().run(methodDesc.lowir)
      }
    }
    methodDescs.clone().each { MethodDescriptor methodDesc ->
      if ('iva' in opts) {

        def iva = new InductionVariableAnalysis()
        iva.analize(methodDesc)
        def depAnal = new DependencyAnalizer()
        def inToOut = depAnal.computeLoopNest(iva.loopAnal.loops)
        depAnal.identifyOutermostLoops(iva.loopAnal.loops).each { outermostLoop ->
          //forbid the easy cases
          if (outermostLoop in iva.foundComplexInductionVarInLoop) return
          if (outermostLoop.body.findAll{it instanceof LowIrStore && it.index == null}.size() > 0) return
          def loadDescs = outermostLoop.body.findAll{it instanceof LowIrLoad}.collect{it.desc}
          def storeDescs = outermostLoop.body.findAll{it instanceof LowIrStore}.collect{it.desc}
//          if (loadDescs.intersect(storeDescs).size() > 0) return
          if (storeDescs.unique{it.hashCode()}.size() != storeDescs.size()) return
          if (outermostLoop.body.findAll{it instanceof LowIrMethodCall ||
                                         it instanceof LowIrCallOut ||
                                         it instanceof LowIrReturn}.size() > 0) return
          try {
            def writes = depAnal.extractWritesInSOPForm(outermostLoop, iva.basicInductionVars, iva.domComps)
            if (writes.size() == 0) return
            def reads = []
            //see if this requires antidependency analysis
            if (loadDescs.intersect(storeDescs).size() > 0)
              reads = depAnal.extractReadsInSOPForm(outermostLoop, iva.basicInductionVars, iva.domComps)

            inToOut.keySet().each {inner ->
              //only relocate IV bounds to their parent loop (don't break nesting)
              if (!outermostLoop.body.contains(inner.header)) return
              //find the current loop's basic IV
              def iv = iva.basicInductionVars.find{it.loop == inner}
              //relocate the IV's bounds
              depAnal.speculativelyMoveInnerLoopInvariantsToOuterLoop(
                methodDesc.lowir,
                outermostLoop,
                [iv.lowBoundTmp, iv.highBoundTmp]
              )
            }
            //at this point, we can relocate the invariants in the SOP form
            def parallelize = new LowIrNode(metaText: 'parallelizable')
            //relocate all read invariants
            reads.each { addr ->
              depAnal.speculativelyMoveInnerLoopInvariantsToOuterLoop(
                methodDesc.lowir,
                outermostLoop,
                addr.ivToInvariants.values().flatten().findAll{it != 1} + addr.invariants
              )
            }
            writes.each { addr ->
              depAnal.speculativelyMoveInnerLoopInvariantsToOuterLoop(
                methodDesc.lowir,
                outermostLoop,
                addr.ivToInvariants.values().flatten().findAll{it != 1} + addr.invariants
              )
              //now, we generate the runtime check
              def domComps = new DominanceComputations()
              domComps.computeDominators(methodDesc.lowir)
              def landingPad = outermostLoop.header.predecessors.find{domComps.dominates(it, outermostLoop.header)}
              LowIrNode.unlink(landingPad, outermostLoop.header)
              def oldLoopBegin = new LowIrNode(metaText: 'parallelize fail dest')
              def check = depAnal.generateParallelizabilityCheck(
                methodDesc,
                addr,
                reads.findAll{it.node.desc == addr.node.desc},
                parallelize,
                oldLoopBegin
              )
              LowIrNode.link(landingPad, check)
              LowIrNode.link(oldLoopBegin, outermostLoop.header)
            }
            def parallelFuncPostfix = "par_${methodDesc.name}_${outermostLoop.header.hashCode() % 100}"
            //find all loop invariant tempVars in the possibly parallelizable loop
            def tmpsInLoop = new LinkedHashSet(outermostLoop.body*.getUses().flatten())
            def domComps = new DominanceComputations()
            domComps.computeDominators(methodDesc.lowir)
            def loopInvariants = new ArrayList(tmpsInLoop.findAll{domComps.dominates(it.defSite, outermostLoop.header)})
            def outermostInductionVar = iva.basicInductionVars.find{it.loop == outermostLoop}
            //get the outer loops bounds into a known location (positions 0 and 1)
            assert loopInvariants.remove(outermostInductionVar.lowBoundTmp)
            assert loopInvariants.remove(outermostInductionVar.highBoundTmp)
            loopInvariants.add(0, outermostInductionVar.highBoundTmp)
            loopInvariants.add(0, outermostInductionVar.lowBoundTmp)
            //create the loop invariant-containing array
            def loopInvariantArray = new VariableDescriptor(
              name: "array_$parallelFuncPostfix",
              type: Type.INT_ARRAY,
              arraySize: loopInvariants.size()
            )
            ast.symTable[loopInvariantArray.name] = loopInvariantArray
            //generate the parallel function
            def parallelMethodDesc = new MethodDescriptor(
              name: "method_$parallelFuncPostfix",
              returnType: Type.VOID,
            )
            def copiedLoop = LoopAnalizer.copyLoop(outermostLoop, parallelMethodDesc.tempFactory)
            //generate loads and stores for all the invariants from the loop
            def loadInvarsList = []
            def storeInvarsList = []
            if (debug) {
              loadInvarsList << new LowIrStringLiteral(
                value: 'Executing parallel thread %d\\n',
                tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
              )
              loadInvarsList << new LowIrCallOut(
                name: 'printf',
                paramTmpVars: [loadInvarsList[-1].tmpVar, new TempVar(type: TempVarType.PARAM, id: 0)],
                tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
              )
            }
            def lowerBoundTmpInNewFunc, upperBoundTmpInNewFunc
            loopInvariants.eachWithIndex{ invariant, index ->
              storeInvarsList << new LowIrIntLiteral(
                value: index,
                tmpVar: methodDesc.tempFactory.createLocalTemp()
              )
              storeInvarsList << new LowIrStore(
                desc: loopInvariantArray,
                index: storeInvarsList[-1].tmpVar,
                value: invariant
              )
              loadInvarsList << new LowIrIntLiteral(
                value: index,
                tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
              )
              loadInvarsList << new LowIrLoad(
                desc: loopInvariantArray,
                index: loadInvarsList[-1].tmpVar,
                tmpVar: copiedLoop[1][invariant]
              )
              if (index == 0) lowerBoundTmpInNewFunc = loadInvarsList[-1].tmpVar
              if (index == 1) upperBoundTmpInNewFunc = loadInvarsList[-1].tmpVar
              if (debug) {
                loadInvarsList << new LowIrStringLiteral(
                  value: 'Read invariant %d, has value %d\\n',
                  tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
                )
                loadInvarsList << new LowIrCallOut(
                  name: 'printf',
                  paramTmpVars: [loadInvarsList[-1].tmpVar, loadInvarsList[-3].tmpVar, loadInvarsList[-2].tmpVar],
                  tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
                )
              }
            }
            //create copies of various written-to arrays that have reads
            reads*.node*.desc.findAll{it in writes*.node*.desc}.each { desc ->
              def copyArray = new VariableDescriptor(
                name: "${desc.name}_invar",
                type: Type.INT_ARRAY,
                arraySize: desc.arraySize + 16 //make it bigger so that we can overcopy easily
              )
              ast.symTable[copyArray.name] = copyArray
              copiedLoop[0].body.findAll{it instanceof LowIrLoad && it.desc == desc}.each { load ->
                load.desc = copyArray
              }
              storeInvarsList << new LowIrCopyArray(src: desc, dst: copyArray)
            }
            //now, we must recompute the loop bounds on a per-thread basis
            loadInvarsList << new LowIrBinOp(
              leftTmpVar: upperBoundTmpInNewFunc,
              rightTmpVar: lowerBoundTmpInNewFunc,
              tmpVar: parallelMethodDesc.tempFactory.createLocalTemp(),
              op: BinOpType.SUB
            )
            loadInvarsList << new LowIrIntLiteral(
              value: 4,
              tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
            )
            //the result of this division is the increment
            loadInvarsList << new LowIrBinOp(
              leftTmpVar: loadInvarsList[-2].tmpVar,
              rightTmpVar: loadInvarsList[-1].tmpVar,
              tmpVar: parallelMethodDesc.tempFactory.createLocalTemp(),
              op: BinOpType.DIV
            )
            def loadIncTmpVar = loadInvarsList[-1].tmpVar
            loadInvarsList << new LowIrBinOp(
              leftTmpVar: new TempVar(type: TempVarType.PARAM, id: 0), //threadid \in {0,1,2,3}
              rightTmpVar: loadInvarsList[-1].tmpVar,
              tmpVar: parallelMethodDesc.tempFactory.createLocalTemp(),
              op: BinOpType.MUL
            )
            //this is the new lower bound
            loadInvarsList << new LowIrBinOp(
              leftTmpVar: lowerBoundTmpInNewFunc,
              rightTmpVar: loadInvarsList[-1].tmpVar,
              tmpVar: lowerBoundTmpInNewFunc,
              op: BinOpType.ADD
            )
            //maybe we compute the upper bound if threadid != 3
            loadInvarsList << new LowIrIntLiteral(
              value: 3,
              tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
            )
            def threadIdCmp = new LowIrBinOp(
              leftTmpVar: new TempVar(type: TempVarType.PARAM, id: 0), //threadid \in {0,1,2,3}
              rightTmpVar: loadInvarsList[-1].tmpVar,
              tmpVar: parallelMethodDesc.tempFactory.createLocalTemp(),
              op: BinOpType.NEQ
            )
            loadInvarsList << threadIdCmp

            def redoUpperBound = new LowIrBinOp(
              leftTmpVar: lowerBoundTmpInNewFunc,
              rightTmpVar: loadIncTmpVar,
              tmpVar: upperBoundTmpInNewFunc,
              op: BinOpType.ADD
            )
            def leaveUpperBound = new LowIrNode(metaText: 'threadid == 3')
            def anotherHeaderNoOp = new LowIrNode(metaText: 'another header noop')
            LowIrNode.link(redoUpperBound, anotherHeaderNoOp)
            LowIrNode.link(leaveUpperBound, anotherHeaderNoOp)
            if (debug) {
              def strLit = new LowIrStringLiteral(
                value: 'Thread %d taking iteration range [%d, %d]\\n',
                tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
              )
              def printf = new LowIrCallOut(
                name: 'printf',
                paramTmpVars: [
                  strLit.tmpVar,
                  new TempVar(type: TempVarType.PARAM, id: 0),
                  lowerBoundTmpInNewFunc,
                  upperBoundTmpInNewFunc
                ],
                tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
              )
              LowIrNode.link(anotherHeaderNoOp, strLit)
              LowIrNode.link(strLit, printf)
              LowIrNode.link(printf, copiedLoop[0].header)
            } else {
              LowIrNode.link(anotherHeaderNoOp, copiedLoop[0].header)
            }
            //TODO: note that this must be the last optimization we break SSA
            
            def loadInvarsBridge = new LowIrBridge(loadInvarsList)
            LowIrGenerator.static_shortcircuit(
              new LowIrValueBridge(threadIdCmp),
              redoUpperBound,
              leaveUpperBound
            )
            copiedLoop[0].exit.falseDest = new LowIrReturn()
            LowIrNode.link(copiedLoop[0].exit, copiedLoop[0].exit.falseDest)
            def parallelMethodStartNode = loadInvarsBridge.begin
            parallelMethodDesc.lowir = loadInvarsBridge.begin
            parallelMethodDesc.params = [new VariableDescriptor(
              name: 'threadid',
              type: Type.INT,
              tmpVar: parallelMethodDesc.tempFactory.createLocalTemp()
            )]
            methodDescs << parallelMethodDesc

            //now, we can just try calling the parallel function after loading the array up
            if (debug) {
              storeInvarsList << new LowIrStringLiteral(
                value: 'Executing parallel codes\\n',
                tmpVar: methodDesc.tempFactory.createLocalTemp()
              )
              storeInvarsList << new LowIrCallOut(
                name: 'printf',
                paramTmpVars: [storeInvarsList[-1].tmpVar],
                tmpVar: methodDesc.tempFactory.createLocalTemp()
              )
            }
            storeInvarsList << new LowIrParallelizedLoop(func: parallelMethodDesc)
            def parallelBridge = new LowIrBridge(storeInvarsList)
            LowIrNode.link(parallelize, parallelBridge.begin)
            LowIrNode.link(parallelBridge.end, outermostLoop.exit.falseDest)
            println "Generated parallel codes"
          } catch (UnparallelizableException e) {
            print "$outermostLoop isn't parallelizable: error on line "
            println e.stackTrace.find{it.className.contains('DependencyAnal')}.lineNumber
          }
        }
      }
    }
    methodDescs.each { methodDesc ->
      if ('cc' in opts)
        new ConditionalCoalescing().analize(methodDesc)

      if ('regalloc' in opts) {
        println "stepping out of ssa form before register allocation."
        SSAComputer.destroyAllMyBeautifulHardWork(methodDesc.lowir);
        println "Register Allocator running!"
        methodDesc.ra = new RegisterAllocator(methodDesc)
        println "--------------------------------------------------------------"
        println "Running Register Allocation for the method: ${methodDesc.name}"
        println "--------------------------------------------------------------"
        methodDesc.ra.RunRegAllocToFixedPoint()
        println "now coloring the lowir for method: ${methodDesc.name}"
        //methodDesc.ra.ColorLowIr();
      }
    }
  }

  CodeGenerator codeGen;

  def genTmpVars = {->
    depends(inter)
    //locals, temps, and params
    methodDescs.each { MethodDescriptor methodDesc ->
      methodDesc.tempFactory.decorateMethodDesc()
    }
  }

  def genCode = {->
    depends(genLowIr)
    depends(stepOutOfSSA)

    // Here we pick which type of code generator we will use
    codeGen = ('regalloc' in opts) ? (new RegAllocCodeGen()) : (new CodeGenerator());

    //make space for globals
    ast.symTable.@map.each { name, desc ->
      name += '_globalvar'
      def s = desc.arraySize
      if (s == null) {
        s = 1
      } else {
        //round up to nearest 16 byte interval
        s += 4
        s = s - (s % 4)
      }
      codeGen.emit('bss', ".comm $name, ${4*s}, 16")
    }

    methodDescs.each { methodDesc ->
      // Calculate traces for each method
      if('regalloc' in opts)
        methodDesc.ra.ColorLowIr();
      TraceGraph.calculateTraces(methodDesc.lowir);
      codeGen.handleMethod(methodDesc)
    }
  }

  def codegen = {->
    depends(genCode)
    def file = argparser['o'] ?: this.file + '.s'
    new File(file).text = codeGen.getAsm()
  }
}

class LexerIterator {
  def lexer
  Closure onError

  def each(Closure c) {
    boolean done = false
    def token
    while (!done) {
      try {
        for (token = lexer.nextToken(); token.type != EOF; token = lexer.nextToken()) {
          c(token)
        }
        done = true
      } catch (Exception e) {
        onError(e, lexer)
      }
    }
  }
}

class FatalException extends RuntimeException {
  String msg = 'Encountered too many errors, giving up'
  int code = 0
  String toString() {
    return msg
  }
}
