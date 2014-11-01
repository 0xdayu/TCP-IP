package tcp

import scala.actors.threadpool.Executors

class Demultiplexing(tcp: TCP) extends Runnable {
  var done = true

  val executors = Executors.newCachedThreadPool()

  def run() {
    //will repeat until the thread ends
    while (done) {
      val tuple = tcp.demultiplexingBuff.bufferRead
      if (tuple != null) {
        val socketAndMap = tcp.usedPortHashMap.getOrElse(tuple._3.head.dstPort, null)
        if (socketAndMap == null) {
          // send rst back
          generateRSTSegment(tuple._3)
        } else {
          val conn = tcp.boundedSocketHashMap.getOrElse(socketAndMap._1, null)
          if (!conn.isServerAndListen) {
            // not listen server
            executors.execute(new ReceivedTCPSegmentHandler(conn, tuple._3))
          } else {
            
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