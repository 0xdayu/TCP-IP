package driver

import tcp.TCP
import java.io._

class RecvFile(socket: Int, source: PrintWriter, tcp: TCP) extends Runnable {
  val BufSize = 1024
  var buf: Array[Byte] = _

  def run() {
    try {
      val (newSocket, dstPort, dstIP) = tcp.virAccept(socket)
      tcp.virClose(socket)
      while (true) {
        buf = tcp.virRead(newSocket, BufSize)
        if (buf.length != 0) {
          val str = new String(buf.map(_.toChar))
          source.write(str, 0, str.length)
          source.flush
        }
      }

      tcp.virClose(newSocket)
      source.close
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }
}