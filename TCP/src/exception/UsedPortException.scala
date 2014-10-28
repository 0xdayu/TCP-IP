package exception

class UsedPortException(num: Int) extends Exception {
  override def getMessage(): String = {
    "Port " + num + " has been used. "
  }
}