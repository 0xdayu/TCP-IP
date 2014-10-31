package tcputil

class RecvBuffer(capacity: Int) {
  val buffer = new Array[Byte](capacity)
  var lastByteRead: Int = -1
  var lastByteExpected: Int = -1
  var lastByteRcvd: Int = -1

  var available: Int = capacity

  def read(sizeBuf: Int): Array[Byte] = {
    val realSize = math.min(sizeBuf, capacity - available)
    val buf = new Array[Byte](realSize)

    for (i <- Range(0, realSize)) {
      lastByteRead = (lastByteRead + 1) % capacity
      buf(i) = buffer(lastByteRead)
    }
    available += realSize
    buf
    }
}