package exception

class InvalidSocketException(num : Int) extends Exception {
  override def getMessage(): String = {
    "The socket should from 3 to 65535, but it is " + num
  }
}