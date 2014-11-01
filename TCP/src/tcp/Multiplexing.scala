package tcp

import ip.NodeInterface
import tcputil.ConvertObject
import tcputil.PrintTCPSegment

class Multiplexing(nodeInterface: NodeInterface, tcp: TCP) extends Runnable {
  var done = true

  def run() {
    //will repeat until the thread ends
    while (done) {
      val tuple = tcp.multiplexingBuff.bufferRead
      if (tuple != null) {
        // PrintTCPSegment.printBinary(ConvertObject.TCPSegmentToByte(tuple._3))
        nodeInterface.generateAndSendPacket(tuple._2, nodeInterface.TCP, ConvertObject.TCPSegmentToByte(tuple._3))
      }
    }
  }

  def cancel() {
    done = false
  }
}