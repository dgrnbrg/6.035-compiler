package decaf

class CompilerError {
  FileInfo fileInfo
  String message
  def file

  String toString() {
    if(fileInfo != null){
      "$File: file Line: $fileInfo.line Column: $fileInfo.col: $message"
    } else {
      "$message"
    }
  }
}
