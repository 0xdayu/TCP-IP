package driver

import java.io._
import tcp.TCP

class SendFile(socket: Int, source: BufferedReader, tcp: TCP) extends Runnable {
  val BufSize = 1024
  var count: Long = 0

  def run() {
    try {
      val startTime = System.currentTimeMillis

      val buf = new Array[Char](BufSize)
      var readBytes = source.read(buf, 0, BufSize)
      while (readBytes != -1) {
        count += readBytes.asInstanceOf[Long]
        val writeBytes = tcp.virWriteAll(socket, buf.map(_.toByte).toArray.slice(0, readBytes))
        readBytes = source.read(buf, 0, BufSize)
      }

      tcp.virClose(socket)
      source.close

      val endTime = System.currentTimeMillis

      val effectiveBandwidth = count / (1024 * 1024 * ((endTime - startTime) / 1000.0))

      println("")
      println("Sendfile on socket " + socket + " done, " + "effective bandwidth: " + f"$effectiveBandwidth%.1f")
      print("> ")
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }
}