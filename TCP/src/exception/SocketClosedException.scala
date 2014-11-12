package exception

import java.net.InetAddress

class SocketClosedException(socket : Int) extends Exception {
  override def getMessage(): String = {
    "Socket has been closed. "
  }
}