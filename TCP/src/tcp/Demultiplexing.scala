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
        //        println("Demultiplexing start")
        //        PrintTCPSegment.printBinary(ConvertObject.TCPSegmentToByte(tuple._3))
        //        println("Demultiplexing end")

        val seg = tuple._3

        // valid checksum
        val sum = tcputil.TCPSum.tcpsum(tuple._1, tuple._2, ConvertObject.TCPSegmentToByte(seg))
        if ((sum & 0xfff) != 0) {
          println("This packet has wrong tcp checksum!")
        } else {
          tcp.synchronized {
            val client = tcp.clientHashMap.getOrElse((tuple._2, seg.head.dstPort, tuple._1, seg.head.srcPort), null)
            if (client == null) {
              // maybe server
              val server = tcp.serverHashMap.getOrElse(seg.head.dstPort, null)
              if (server == null) {
                // send rst back
                generateRSTSegment(tuple._3)
              } else {
                if (server.isServerAndListen) {
                  executors.execute(new ReceivedTCPSegmentHandler(server, tuple._1, tuple._2, seg))
                }
              }
            } else {
              // must be client
              executors.execute(new ReceivedTCPSegmentHandler(client, tuple._1, tuple._2, seg))
            }
          }
        }
      }
    }
  }

  def cancel() {
    done = false
    executors.shutdown
  }

  def generateRSTSegment(seg: TCPSegment) {

  }

}