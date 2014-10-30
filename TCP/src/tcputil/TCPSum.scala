package tcputil

import ip.{ IPHead, IPPacket }
import iputil.IPSum
import tcp.TCPHead
import java.net.InetAddress

object TCPSum {
  def tcpsum(pkt: IPPacket): Int = {
    val pesudeHead = new Array[Byte](12)

    toByteAddr(pesudeHead, 0, pkt.head.saddr)
    toByteAddr(pesudeHead, 4, pkt.head.daddr)
    pesudeHead(8) = 0
    pesudeHead(9) = (pkt.head.protocol & 0xff).asInstanceOf[Byte]
    pesudeHead(10) = ((pkt.payLoad.length >> 8) & 0xff).asInstanceOf[Byte]
    pesudeHead(11) = (pkt.payLoad.length & 0xff).asInstanceOf[Byte]

    (IPSum.ipsum(pesudeHead ++ pkt.payLoad) & 0xffff).asInstanceOf[Int]
  }
  
  def toByteAddr(buf: Array[Byte], i: Int, addr: InetAddress) {
    var count = i
    for (b <- addr.getAddress()) {
      buf(count) = b
      count += 1
    }
  }
}