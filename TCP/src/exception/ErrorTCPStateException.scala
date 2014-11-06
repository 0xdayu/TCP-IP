package exception

class ErrorTCPStateException(state: tcputil.TCPState.Value) extends Exception {
  override def getMessage(): String = {
    "Error TCP State Exception: " + state
  }
}