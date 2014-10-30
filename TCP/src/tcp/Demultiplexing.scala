package tcp

class Demultiplexing(tcp: TCP) extends Runnable {
  var done = true

  def run() {
    //will repeat until the thread ends
    while (done) {
    	tcp.demultiplexingLock.lock
    	val seg = tcp.demultiplexingBuff.bufferRead
    	if (seg != null){
    	  val conn = tcp.usedPortHashMap.getOrElse(seg.head.dstPort, null)
    	  conn.recvBufLock.lock
    	  //TODO: need to implement
    	  //conn.recvBuf.write(, conn)
    	  conn.recvBufLock.unlock
    	}
    	tcp.demultiplexingLock.unlock
    }
  }

  def cancel() {
    done = false
  }

}