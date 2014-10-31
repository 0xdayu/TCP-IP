package tcputil

class SendBuffer(capacity: Int) {
  val buffer = new Array[Byte](capacity)
  var lastByteAcked: Int = -1
  var lastByteSent: Int = -1
  var lastByteWritten: Int = -1

  var available: Int = capacity

  def write(buf: Array[Byte]): Int = {
    val realSize = math.min(buf.size, available)

    for (i <- Range(0, realSize)) {
      lastByteWritten = (lastByteWritten + 1) % capacity
      buffer(lastByteWritten) = buf(i)
    }
    available -= realSize
    realSize
  }

  // read all data need to be resent
  def readFromLastByteAcked(len: Int): Array[Byte] = {
//    if (lastByteWritten >= lastByteAcked) {
//    	 val realSize = math.min(buf.size, available)
//    } else {
//
//    }
    null
  }

  // read len data 
  def readFromLastByteSent(len: Int): Array[Byte] = {
    null
  }

  def increaseLastByteAcked(len: Int) = {
    lastByteAcked = (lastByteAcked + len) % capacity
    available += len
  }
}