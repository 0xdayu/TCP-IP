package exception

class ReadBlockException extends Exception {
  override def getMessage(): String = {
    "Read has been closed"
  }
}