import decaf.GroovyMain

/*
The automation runner supports 2 cool things: you can expect a particular exit code, and
you can embed into the decaf file what you want the output to be, and to fail if the output
doesn't match. For the former, add a comment anywhere on a line by itself with no indenting
in the following form:
// EXITCODE 3
where 3 is the exitcode you want to occur.
For the latter, add one comment per line with the same structure, but use this form:
// EXPECTS Then you can put any text here which will be diffed with the output
// EXPECTS multiline expectations work too :)
*/

def attempted = 0
def succeeded = 0
def failList = []
def failFile = new File('failed_automations')
if (failFile.exists() && failFile.text != '') {
  failFile.eachLine {
    def file = new File(it)
    if (!file.name.endsWith('dcf')) return
    attempted++
    if (AutomationTester.test(file)) succeeded++
    else failList << file
  }
} else {
  new File('TestPrograms').eachFile { file ->
    if (!file.name.endsWith('dcf')) return
    attempted++
    if (AutomationTester.test(file)) succeeded++
    else failList << file
  }
}

println "\nSummary: $succeeded/$attempted passed"

failFile.text = failList.inject(''){str, elt -> str + elt + '\n'}

//cleanup
new File('tmp.s').delete()
new File('tmp.o').delete()

class AutomationTester {
 static boolean test(file) {
  //only compile decaf programs
  //output to tmp.s, use assertions
  def compiler = GroovyMain.runMain('codegen', file.text, ['assertEnabled': true, 'o': 'tmp.s'])

  //if compilation failed, print error and terminate
  if (compiler.failException) {
    def e = compiler.failException
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
    if (compiler.errors) {
      compiler.errors.each{println it}
    }
    return false
  }

  //try to run gcc
  Process gcc = "gcc tmp.s -o tmp.o".execute()
  def gccout = new StringBuffer()
  def gccerr = new StringBuffer()
  gcc.consumeProcessOutput(gccout, gccerr)
  gcc.waitFor()
  if (gccout.toString() != '' || gccerr.toString() != '') {
    //output should be silent, print issue and terminate
    println "gcc stdout:\n$gccout\ngcc stderr:\n$gccerr"
    return false
  }

  //try to run our application
  Process app = "./tmp.o".execute()
  def appout = new StringBuffer()
  def apperr = new StringBuffer()
  app.consumeProcessOutput(appout, apperr)
  def expectedExitCode = 0
  def expectedOutput = null
  file.eachLine {
    if (it.startsWith('// EXITCODE ')) {
      expectedExitCode = (it =~ '// EXITCODE (\\d*)')[0][1] as int
    } else if (it.startsWith('// EXPECTS ')) {
      if (!expectedOutput) expectedOutput = ''
      expectedOutput += (it =~ '// EXPECTS (.*)')[0][1] + '\n'
    }
  }
  app.waitFor()
  if (app.exitValue() == expectedExitCode && (expectedOutput == null || expectedOutput == appout.toString())) {
    println "$file.name succeeded!"
    return true
  } else {
    println "$file.name failed.\nstdout:\n$appout"
    if (expectedOutput) println "Expected output:\n$expectedOutput"
    return false
  }
 }
}
