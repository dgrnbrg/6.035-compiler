package decaf
import antlr.*
import groovy.util.*
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
//    out.println("${cur.hashCode()} [label=\"$cur $cur.label\\n${cur.anno['expr']}\\n${cur.anno['insert']}\\n${cur.anno['delete']}\"$nodeColor]")
    out.println("${cur.hashCode()} [label=\"$cur $cur.label\\n${cur.anno['instVal']}\"$nodeColor]")
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
    if ('inline' in argparser['opt'] || 'all' in argparser['opt']) {
      lowirGen.inliningThreshold = 50
    } else {
      lowirGen.inliningThreshold = 0
    }
    if ('all' in argparser['opt']) {
      opts += ['ssa', 'dce', 'pre', 'cp', 'sccp', 'dse', 'iva']
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

  def lowir = {->
    depends(setupDot)
    depends(genLowIr)


    dotOut.println('digraph g {')
    methodDescs.each { methodDesc ->
//      SSAComputer.destroyAllMyBeautifulHardWork(methodDesc.lowir)
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
    methodDescs.clone().each { MethodDescriptor methodDesc ->
      if ('ssa' in opts)
        new SSAComputer().compute(methodDesc)
      if ('cse' in opts)
        new CommonSubexpressionElimination().run(methodDesc)
      if ('sccp' in opts)
        new SparseConditionalConstantPropagation().run(methodDesc)
      if ('cp' in opts)
        new CopyPropagation().propagate(methodDesc.lowir)
      if ('dce' in opts)
        new AggressiveDCE().run(methodDesc.lowir)
      if ('dse' in opts)
        new DeadStoreElimination().run(methodDesc.lowir)
      if ('pre' in opts) {
        def repeats = 0
        def stillGoing = true
        while (repeats < 2 && stillGoing) {
          def lcm = new LazyCodeMotion()
          lcm.run(methodDesc)
          new CopyPropagation().propagate(methodDesc.lowir)
          new AggressiveDCE().run(methodDesc.lowir)
          stillGoing = lcm.insertCnt != lcm.deleteCnt
          repeats++
        }
        new DeadStoreElimination().run(methodDesc.lowir)
      }
      if ('iva' in opts) {

        def iva = new InductionVariableAnalysis()
        iva.analize(methodDesc)
        def depAnal = new DependencyAnalizer()
        def inToOut = depAnal.computeLoopNest(iva.loopAnal.loops)
        depAnal.identifyOutermostLoops(iva.loopAnal.loops).each { outermostLoop ->
          //forbid the easy cases
          if (iva.foundComplexInductionVar) return
          if (outermostLoop.body.findAll{it instanceof LowIrStore && it.index == null}.size() > 0) return
          def loadDescs = outermostLoop.body.findAll{it instanceof LowIrLoad}.collect{it.desc}
          def storeDescs = outermostLoop.body.findAll{it instanceof LowIrStore}.collect{it.desc}
          if (loadDescs.intersect(storeDescs).size() > 0) return
          if (storeDescs.unique{it.hashCode()}.size() != storeDescs.size()) return
          if (outermostLoop.body.findAll{it instanceof LowIrMethodCall ||
                                         it instanceof LowIrCallOut ||
                                         it instanceof LowIrReturn}.size() > 0) return
          try {
            def writes = depAnal.extractWritesInSOPForm(outermostLoop, iva.basicInductionVars, iva.domComps)
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
            def loopInvariants = tmpsInLoop.findAll{domComps.dominates(it.defSite, outermostLoop.header)}
            def loopInvariantArray = new VariableDescriptor(
              name: "array_$parallelFuncPostfix",
              type: Type.INT_ARRAY,
              arraySize: loopInvariants.size()
            )
            ast.symTable[loopInvariantArray.name] = loopInvariantArray
            codeGen.emit('bss', ".comm ${loopInvariantArray.name}_globalvar ${8*loopInvariantArray.arraySize}")
            //generate the parallel function
            def parallelMethodDesc = new MethodDescriptor(
              name: "method_$parallelFuncPostfix",
              returnType: Type.VOID,
            )
            def copiedLoop = LoopAnalizer.copyLoop(outermostLoop, parallelMethodDesc.tempFactory)
            //generate loads and stores for all the invariants from the loop
            def loadInvarsList = []
            def storeInvarsList = []
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
            def loadInvarsBridge = new LowIrBridge(loadInvarsList)
            LowIrNode.link(loadInvarsBridge.end, copiedLoop[0].header)
            copiedLoop[0].exit.falseDest = new LowIrReturn()
            LowIrNode.link(copiedLoop[0].exit, copiedLoop[0].exit.falseDest)
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
            storeInvarsList << new LowIrMethodCall(
              descriptor: parallelMethodDesc,
              paramTmpVars: [loopInvariants.iterator().next()],
              tmpVar: methodDesc.tempFactory.createLocalTemp()
            )
            def parallelBridge = new LowIrBridge(storeInvarsList)
            LowIrNode.link(parallelize, parallelBridge.begin)
            LowIrNode.link(parallelBridge.end, outermostLoop.exit.falseDest)
          } catch (UnparallelizableException e) {
            print "$outermostLoop isn't parallelizable: error on line "
            println e.stackTrace.find{it.className.contains('DependencyAnal')}.lineNumber
          }
        }
      }
    }
  }

  def codeGen = new CodeGenerator()

  def genTmpVars = {->
    depends(inter)
    //locals, temps, and params
    methodDescs.each { MethodDescriptor methodDesc ->
      methodDesc.tempFactory.decorateMethodDesc()
    }
    //make space for globals
    ast.symTable.@map.each { name, desc ->
      name += '_globalvar'
      def s = desc.arraySize
      if (s == null) s = 1
      codeGen.emit('bss', ".comm $name ${8*s}")
    }
  }

  def genCode = {->
    depends(genLowIr)
    methodDescs.each { methodDesc ->
      SSAComputer.destroyAllMyBeautifulHardWork(methodDesc.lowir)
      // Calculate traces for each method
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
