package exception

class UnboundSocketException(num : Int) extends Exception {
  override def getMessage(): String = {
    "Socket " + num + " is has not been bounded"
  }
}