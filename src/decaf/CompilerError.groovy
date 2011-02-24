package decaf

class CompilerError {
  FileInfo fileInfo
  String message
  def file

  String toString() {
    "$File: file Line: $fileInfo.line Column: $fileInfo.col: $message"
  }
}
