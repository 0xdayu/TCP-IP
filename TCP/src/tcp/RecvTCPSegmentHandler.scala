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
      var conn: TCPConnection = null
      tcp.synchronized {
        val client = tcp.clientHashMap.getOrElse((tuple._2, seg.head.dstPort, tuple._1, seg.head.srcPort), null)
        if (client == null) {
          // maybe server
          val server = tcp.serverHashMap.getOrElse(seg.head.dstPort, null)
          if (server == null) {
            // send rst back
            // generateRSTSegment(tuple._3)
          } else {
            if (server.isServerAndListen) {
              conn = server
            }
          }
        } else {
          // must be client
          conn = client
        }
      }

      // make sure connection can be got
      if (conn != null) {
        conn.connectionBehavior(tuple._1, tuple._2, seg)
      } else {
        // reset
        tcp.multiplexingBuff.bufferWrite(tuple._2, tuple._1, generateRSTSegment(tuple._3))
      }
    }

  }

  def generateRSTSegment(seg: TCPSegment): TCPSegment = {
    val newTCPHead = new TCPHead
    newTCPHead.srcPort = seg.head.dstPort
    newTCPHead.dstPort = seg.head.srcPort
    newTCPHead.seqNum = 0
    newTCPHead.ackNum = 0
    newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
    newTCPHead.winSize = 0

    // rst
    newTCPHead.rst = 1

    val newTCPSegment = new TCPSegment

    newTCPSegment.head = newTCPHead
    newTCPSegment.payLoad = new Array[Byte](0)

    newTCPSegment
  }
}