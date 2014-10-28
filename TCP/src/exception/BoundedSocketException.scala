package exception

class BoundedSocketException(num : Int) extends Exception{
  override def getMessage(): String = {
    "The socket " + num + " has been bounded"
  }
}