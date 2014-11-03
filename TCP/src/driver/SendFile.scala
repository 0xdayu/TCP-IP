package driver

import scala.io._
import tcp.TCP

class SendFile(socket: Int, source: BufferedSource, tcp: TCP) extends Runnable {
  val BufSize = 1024
  var writeBytes = 0

  def run() {
    try {
      val buf: Array[Byte] = source.map(_.toByte).toArray
      while (writeBytes < buf.length) {
        val ret = tcp.virWriteAll(socket, buf.slice(writeBytes, writeBytes + BufSize))
        writeBytes += ret
      }

      tcp.virClose(socket)
      source.close
      println("sendfile on socket " + socket + " done")
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }
}