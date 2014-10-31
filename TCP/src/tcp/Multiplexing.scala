package tcp

import ip.NodeInterface
import tcputil.ConvertObject

class Multiplexing(nodeInterface: NodeInterface, tcp: TCP) extends Runnable {
  var done = true

  def run() {
    //will repeat until the thread ends
    while (done) {
      val seg = tcp.multiplexingBuff.bufferRead
      if (seg != null) {
        val conn = tcp.usedPortHashMap.getOrElse(seg.head.srcPort, null)
        nodeInterface.generateAndSendPacket(conn.dstIP, nodeInterface.TCP, ConvertObject.TCPSegmentToByte(seg))
      }
    }
  }

  def cancel() {
    done = false
  }

}