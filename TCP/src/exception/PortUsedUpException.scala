package exception

class PortUsedUpException extends Exception{
  override def getMessage(): String = {
    "The port from 1024 to 65535 has been used. "
  }
}