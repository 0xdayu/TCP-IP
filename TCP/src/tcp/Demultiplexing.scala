package tcp

import scala.actors.threadpool.Executors
import tcputil._

class Demultiplexing(tcp: TCP) extends Runnable {
  var done = true

  val executors = Executors.newCachedThreadPool()

  def run() {
    // will repeat until the thread ends
    while (done) {
      val tuple = tcp.demultiplexingBuff.bufferRead
      if (tuple != null) {
        println("Demultiplexing start")
        PrintTCPSegment.printBinary(ConvertObject.TCPSegmentToByte(tuple._3))
        println("Demultiplexing end")

        executors.execute(new ReceivedTCPSegmentHandler(tuple, tcp))
      }
    }
  }

  def cancel() {
    done = false
    executors.shutdown
  }
}