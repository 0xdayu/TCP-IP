package exception

class InvalidPortException(num : Int) extends Exception {
  override def getMessage(): String = {
    num + " is not valid. Range should be 1024 - 65535."
  }
}