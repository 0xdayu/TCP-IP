package driver

import java.io._
import tcp.TCP

class SendFile(socket: Int, source: BufferedReader, tcp: TCP) extends Runnable {
  val BufSize = 1024
  var off = 0

  def run() {
    try {
      val buf = new Array[Char](BufSize)
      var readBytes = source.read(buf, off, BufSize)
      while (readBytes != -1) {
        val writeBytes = tcp.virWriteAll(socket, buf.map(_.toByte).toArray.slice(0, readBytes))
        off += writeBytes
      }

      tcp.virClose(socket)
      source.close
      println("sendfile on socket " + socket + " done")
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }
}