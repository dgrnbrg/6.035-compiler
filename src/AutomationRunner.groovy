import decaf.GroovyMain

def attempted = 0
def succeeded = 0
new File('TestPrograms').eachFile { file ->
  //only compile decaf programs
  if (!file.name.endsWith('dcf')) return
  attempted++
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
    return
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
    return
  }

  //try to run our application
  Process app = "./tmp.o".execute()
  def appout = new StringBuffer()
  def apperr = new StringBuffer()
  app.consumeProcessOutput(appout, apperr)
  def expectedExitCode = 0
  file.eachLine {
    if (it.startsWith('// EXITCODE ')) {
      expectedExitCode = (it =~ '// EXITCODE (\\d*)')[0][1] as int
    }
  }
  app.waitFor()
  if (app.exitValue() == expectedExitCode) {
    println "$file.name succeeded!"
    succeeded++
  } else {
    println "$file.name failed. stdout:\n$appout\nstderr:\n$apperr"
  }
}

println "\nSummary: $succeeded/$attempted passed"

//cleanup
new File('tmp.s').delete()
new File('tmp.o').delete()
