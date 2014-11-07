package tcp

class DataSending(conn: TCPConnection, tcp: TCP) extends Runnable {
  var done = true
  var preAck: Long = _

  def run() {
    // first ack
    preAck = conn.getAck
    while (done) {
      val payload = conn.sendBuf.read(tcp.DefaultMSS)
      // TODO: Lock for getAck
      if (payload.length != 0 || preAck != conn.getAck) {
        preAck = conn.getAck
        tcp.multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, conn.generateTCPSegment(payload))
      }
    }
  }

  def cancel() {
    done = false
  }
}