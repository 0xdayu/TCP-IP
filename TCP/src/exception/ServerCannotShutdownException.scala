package exception

class ServerCannotShutdownException extends Exception{
  override def getMessage(): String = {
    "Server cannot be shutdown!"
  }
}