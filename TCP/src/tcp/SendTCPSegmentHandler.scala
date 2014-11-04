package tcp

import java.net.InetAddress
import ip.NodeInterface
import tcputil.ConvertObject

class SendTCPSegmentHandler(tuple: (InetAddress, InetAddress, TCPSegment), nodeInterface: NodeInterface, tcp: TCP) extends Runnable {
  def run() {
    // add checksum
    val sum = tcputil.TCPSum.tcpsum(tuple._1, tuple._2, ConvertObject.TCPSegmentToByte(tuple._3))
    tuple._3.head.checkSum = sum
    nodeInterface.generateAndSendPacket(tuple._2, nodeInterface.TCP, ConvertObject.TCPSegmentToByte(tuple._3))
  }
}