package tcp

import java.util.TimerTask

class DataTimeOut(tcp: TCP, conn: TCPConnection) extends TimerTask {
  def run() {
    val buf = conn.sendBuf.retransmit
    var size = 0

    while (buf.length != size) {
      val payload = buf.slice(size, size + tcp.DefaultMSS)
      size += payload.length

      tcp.multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, conn.generateTCPSegment(payload))
    }
  }
}