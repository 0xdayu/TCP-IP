package exception

class ServerHasCloseException extends Exception {
  override def getMessage(): String = {
    "Server has closed"
  }
}