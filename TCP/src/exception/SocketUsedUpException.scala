package exception

class SocketUsedUpException extends Exception{
  override def getMessage(): String = {
    "The socket from 3 to 65535 has been used. "
  }
}