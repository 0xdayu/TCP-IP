package tcputil

import scala.collection.mutable.Queue
import tcp.TCPSegment
import java.net.InetAddress

class FIFOBuffer(capacity: Int) {
  val buffer = new Queue[(InetAddress, InetAddress, TCPSegment)]
  var size = 0

  def getCapacity: Int = capacity

  def getSize: Int = this.synchronized { size }

  def getAvailable: Int = this.synchronized { capacity - size }

  def isFull: Boolean = this.synchronized { capacity == size }

  def isEmpty: Boolean = this.synchronized { size == 0 }

  def bufferWrite(src: InetAddress, dst: InetAddress, seg: TCPSegment) {
    this.synchronized {
      val len = seg.head.dataOffset + seg.payLoad.length
      if (len > capacity - size) {
        println("No enough space to store the segment, drop this segment")
      } else {
        buffer.enqueue((src, dst, seg))
        size += len
      }
    }
  }

  def bufferRead(): (InetAddress, InetAddress, TCPSegment) = {
    this.synchronized {
      if (size == 0) {
        null
      } else {
        val tuple = buffer.dequeue
        size -= tuple._3.head.dataOffset + tuple._3.payLoad.length
        tuple
      }
    }
  }

  def bufferClean() {
    this.synchronized {
      buffer.clear
      size = 0
    }
  }
}