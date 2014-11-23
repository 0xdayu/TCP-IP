package exception

import java.net.InetAddress

class DestinationUnreachableException(ip: InetAddress) extends Exception {
  override def getMessage(): String = {
    "Destination unreachable: " + ip.getHostAddress
  }
}