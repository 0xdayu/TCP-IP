package tcp

import java.util.TimerTask
import tcputil.TCPState

class DataTimeOut(tcp: TCP, conn: TCPConnection) extends TimerTask {
  def run() {
    var buf: Array[Byte] = null
    var seq: Long = 0

    conn.synchronized {
      buf = conn.sendBuf.retransmit
      seq = conn.getSeq
    }
    var size = 0

    if (conn.isEstablished && buf.length != 0) {
      conn.synchronized {
        conn.noReplyCount += 1
      }
      if (conn.noReplyCount == tcp.DefaultMaxNoReplyCount) {
        println("Disconnect with remote node")
        print("> ")
        tcp.cleanTable(conn)
        conn.setState(TCPState.CLOSE)
        return
      }
    }

    while (buf.length != size) {
      val payload = buf.slice(size, size + tcp.DefaultMSS)
      tcp.multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, conn.generateTCPSegment(payload, seq, size))

      size += payload.length
    }

    // reduce cwd
    conn.congestionControl(1, 0, 0)
  }
}