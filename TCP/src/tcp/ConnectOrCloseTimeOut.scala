package tcp

import java.util.TimerTask

class ConnectOrCloseTimeOut(tcp: TCP, conn: TCPConnection, clone: TCPSegment) extends TimerTask {
  def run() {
    var count = 1
    while (count <= tcp.DefaultReTransmitTimes) {
      tcp.multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, tcputil.ConvertObject.cloneSegment(clone))

      Thread.sleep(tcp.DefaultConnectOrCloseTimeout)
      count += 1
    }

    println("Timeout this connection.")

    // clean the table and close
    tcp.cleanTable(conn)
    conn.setState(tcputil.TCPState.CLOSE)
  }
}