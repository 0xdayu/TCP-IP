package exception

class WriteBlockException extends Exception {
  override def getMessage(): String = {
    "Write has been closed"
  }
}