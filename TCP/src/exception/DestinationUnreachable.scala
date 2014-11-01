package exception

import java.net.InetAddress

class DestinationUnreachable(ip: InetAddress) extends Exception {
  override def getMessage(): String = {
    "Destination unreachable: " + ip.getHostAddress
  }
}