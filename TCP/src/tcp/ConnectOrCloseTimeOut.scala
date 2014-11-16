package tcp

import java.util.TimerTask

class ConnectOrCloseTimeOut(tcp: TCP, conn: TCPConnection, seg: TCPSegment) extends TimerTask {
  def run() {
    tcp.multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, seg)
  }
}