package tcputil

class CircularArray(capacity: Int) {
  val buffer = new Array[Byte](capacity)
  var read = 0 // next read
  var write = 0 // next write
  var size = 0

  def getCapacity: Int = capacity

  def getSize: Int = this.synchronized { size }

  def getAvailable: Int = this.synchronized { capacity - size }

  def isFull: Boolean = this.synchronized { capacity == size }

  def isEmpty: Boolean = this.synchronized { size == 0 }

  def write(buf: Array[Byte]): Int = {
    this.synchronized {
      val realSize = math.min(buf.size, capacity - size)
      
      for (i <- Range(0, realSize)) {
        buffer(write) = buf(i)
        write = (write + 1) % capacity
      }
      size += realSize
      realSize
    }
  }

  def read(sizeBuf: Int): Array[Byte] = {
    this.synchronized {
      val realSize = math.min(sizeBuf, size)
      val buf = new Array[Byte](realSize)
      
      for (i <- Range(0, realSize)) {
        buf(i) = buffer(read)
        read = (read + 1) % capacity
      }
      size -= realSize
      buf
    }
  }
}