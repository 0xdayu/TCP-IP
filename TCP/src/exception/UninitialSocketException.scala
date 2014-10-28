package exception

class UninitialSocketException(num: Int) extends Exception {
  override def getMessage(): String = {
    "The socket " + num + " has not been initiated"
  }
}