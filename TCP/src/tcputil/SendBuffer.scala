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
}