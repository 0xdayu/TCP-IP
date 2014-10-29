package exception

class ErrorTCPStateException extends Exception {
  override def getMessage(): String = {
    "Error TCP State Exception"
  }
}