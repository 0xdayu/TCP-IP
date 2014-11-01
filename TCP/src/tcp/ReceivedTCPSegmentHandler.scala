package tcp

import java.net.InetAddress

class ReceivedTCPSegmentHandler(conn: TCPConnection, srcip: InetAddress, dstip: InetAddress, seg: TCPSegment) extends Runnable {
  def run() {
    conn.connectionBehavior(srcip, dstip, seg)
  }
}