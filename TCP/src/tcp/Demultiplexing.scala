package tcp

import scala.actors.threadpool.Executors

class Demultiplexing(tcp: TCP) extends Runnable {
  var done = true
  
  val executors = Executors.newCachedThreadPool()

  def run() {
    //will repeat until the thread ends
    while (done) {
    	val seg = tcp.demultiplexingBuff.bufferRead
    	if (seg != null){
    	  val conn = tcp.usedPortHashMap.getOrElse(seg.head.dstPort, null)
    	  if (conn == null){
    	    // send rst back
    	    generateRSTSegment(seg)
    	  } else {
    	    executors.execute(new ReceivedTCPSegmentHandler(conn, seg))
    	  }
    	}
    }
  }

  def cancel() {
    done = false
    executors.shutdown
  }
  
  def generateRSTSegment(seg : TCPSegment){
    
  }

}