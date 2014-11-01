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
        println("Multiplexing start")
        PrintTCPSegment.printBinary(ConvertObject.TCPSegmentToByte(tuple._3))
        println("Multiplexing end")

        // add checksum
        val sum = tcputil.TCPSum.tcpsum(tuple._1, tuple._2, ConvertObject.TCPSegmentToByte(tuple._3))
        tuple._3.head.checkSum = sum
        nodeInterface.generateAndSendPacket(tuple._2, nodeInterface.TCP, ConvertObject.TCPSegmentToByte(tuple._3))
      }
    }
  }

  def cancel() {
    done = false
  }
}