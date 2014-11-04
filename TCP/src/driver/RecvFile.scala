package driver

import tcp.TCP
import java.io._

class RecvFile(socket: Int, source: PrintWriter, tcp: TCP) extends Runnable {
  val BufSize = 1024
  var off = 0

  def run() {
    try {
      val (newSocket, dstPort, dstIP) = tcp.virAccept(socket)
      tcp.virClose(socket)

      var buf = tcp.virRead(socket, BufSize)
      while (buf.length != 0) {
        val str = new String(buf.map(_.toChar))
        source.write(str, off, str.length)
        off += str.length
        buf = tcp.virRead(socket, BufSize)
      }

      tcp.virClose(newSocket)
      source.close
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }
}