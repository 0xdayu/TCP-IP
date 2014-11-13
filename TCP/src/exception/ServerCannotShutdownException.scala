package exception

class ServerCannotShutdownException extends Exception {
  override def getMessage(): String = {
    "Server cannot be shutdown of read or write type, only can be shutdown by both type."
  }
}