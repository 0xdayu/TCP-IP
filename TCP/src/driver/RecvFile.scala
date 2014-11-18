package driver

import tcp.TCP
import java.io._
import java.net.InetAddress
import tcp.TCPConnection

class RecvFile(socket: Int, source: PrintWriter, tcp: TCP) extends Runnable {
  val BufSize = 1024 * 10
  var buf: Array[Byte] = _

  var newSocket: Int = _

  var conn: TCPConnection = _

  def run() {
    try {
      newSocket = tcp.virAccept(socket)._1

      tcp.virClose(socket)
      while (true) {
        buf = tcp.virRead(newSocket, BufSize)
        if (buf.length != 0) {
          val str = new String(buf.map(_.toChar))
          source.write(str, 0, str.length)
          source.flush
        }
      }

      conn = tcp.boundedSocketHashMap.getOrElse(newSocket, null)
      if (conn != null) {
        conn.recvBuf.waitAvailable
      }
      tcp.virClose(newSocket)
      source.close
    } catch {
      case e: exception.ServerHasCloseException =>
        {
          conn = tcp.boundedSocketHashMap.getOrElse(newSocket, null)
          if (conn != null) {
            conn.recvBuf.waitAvailable
          }
          tcp.virClose(newSocket)
          source.close
        }
      case e: Exception => println(e.getMessage)
    }
  }
}