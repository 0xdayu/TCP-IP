package tcp

import java.util.TimerTask

class DataTimeOut(tcp: TCP, conn: TCPConnection) extends TimerTask {
  def run() {
    var buf: Array[Byte] = null
    var seq: Long = 0

    conn.synchronized {
      buf = conn.sendBuf.retransmit
      seq = conn.getSeq
    }
    var size = 0

    while (buf.length != size) {
      val payload = buf.slice(size, size + tcp.DefaultMSS)
      tcp.multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, conn.generateTCPSegment(payload, seq, size))

      size += payload.length
    }

    // reduce cwd
    conn.congestionControl(1, 0, 0)
  }
}