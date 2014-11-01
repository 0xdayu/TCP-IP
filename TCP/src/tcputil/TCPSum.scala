package tcputil

import iputil.IPSum
import tcp.TCPHead
import java.net.InetAddress

object TCPSum {
  val tcpProtocol = 6
  def tcpsum(saddr: InetAddress, daddr: InetAddress, seg: Array[Byte]): Int = {
    val pesudeHead = new Array[Byte](12)

    toByteAddr(pesudeHead, 0, saddr)
    toByteAddr(pesudeHead, 4, daddr)
    pesudeHead(8) = 0
    pesudeHead(9) = (tcpProtocol & 0xff).asInstanceOf[Byte]
    pesudeHead(10) = ((seg.length >> 8) & 0xff).asInstanceOf[Byte]
    pesudeHead(11) = (seg.length & 0xff).asInstanceOf[Byte]

    (IPSum.ipsum(pesudeHead ++ seg) & 0xffff).asInstanceOf[Int]
  }

  def toByteAddr(buf: Array[Byte], i: Int, addr: InetAddress) {
    var count = i
    for (b <- addr.getAddress()) {
      buf(count) = b
      count += 1
    }
  }
}