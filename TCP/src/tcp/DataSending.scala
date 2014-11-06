package tcp

class DataSending(conn: TCPConnection, tcp: TCP) extends Runnable {
  var done = true

  def run() {
    while (done) {
      val payload = conn.sendBuf.read(tcp.DefaultMSS)
      if (payload.length != 0) {
        tcp.multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, conn.generateTCPSegment(payload))
      }
    }
  }

  def cancel() {
    done = false
  }
}