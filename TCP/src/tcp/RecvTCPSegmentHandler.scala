package tcp

import java.net.InetAddress
import tcputil.ConvertObject

class RecvTCPSegmentHandler(tuple: (InetAddress, InetAddress, TCPSegment), tcp: TCP) extends Runnable {
  def run() {
    val seg = tuple._3

    // valid checksum
    val sum = tcputil.TCPSum.tcpsum(tuple._1, tuple._2, ConvertObject.TCPSegmentToByte(seg))
    if ((sum & 0xfff) != 0) {
      println("This packet has wrong tcp checksum!")
    } else {
      tcp.synchronized {
        val client = tcp.clientHashMap.getOrElse((tuple._2, seg.head.dstPort, tuple._1, seg.head.srcPort), null)
        if (client == null) {
          // maybe server
          val server = tcp.serverHashMap.getOrElse(seg.head.dstPort, null)
          if (server == null) {
            // send rst back
            generateRSTSegment(tuple._3)
          } else {
            if (server.isServerAndListen) {
              server.connectionBehavior(tuple._1, tuple._2, seg)
            }
          }
        } else {
          // must be client
          client.connectionBehavior(tuple._1, tuple._2, seg)
        }
      }
    }

  }

  def generateRSTSegment(seg: TCPSegment) {

  }
}