package tcp

import scala.actors.threadpool.Executors
import tcputil._

class Demultiplexing(tcp: TCP) extends Runnable {
  var done = true

  // val executors = Executors.newCachedThreadPool()
  val executors = Executors.newFixedThreadPool(tcp.DefaultThreads)

  def run() {
    // will repeat until the thread ends
    while (done) {
      val tuple = tcp.demultiplexingBuff.bufferRead
      if (tuple != null) {
        if (tcp.TraceDemulti) {
          println("Demultiplxing Time: " + System.nanoTime)
          println("Demultiplexing start")
          PrintTCPSegment.printBinary(ConvertObject.TCPSegmentToByte(tuple._3))
          println("Demultiplexing end")
        }

        val ran = scala.util.Random
        if (ran.nextInt(100) >= tcp.DefaultLoss) {
          executors.execute(new RecvTCPSegmentHandler(tuple, tcp))
        } else {
          // println(tcp.DefaultLoss + "% drop the packet!")
        }
      }
    }
  }

  def cancel() {
    done = false
    executors.shutdown
  }
}