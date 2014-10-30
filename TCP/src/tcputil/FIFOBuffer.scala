package tcputil

import scala.collection.mutable.Queue
import tcp.TCPSegment

class FIFOBuffer(capacity: Int) {
  val buffer = new Queue[TCPSegment]
  var size = 0

  def getCapacity: Int = capacity

  def getSize: Int = this.synchronized { size }

  def getAvailable: Int = this.synchronized { capacity - size }

  def isFull: Boolean = this.synchronized { capacity == size }

  def isEmpty: Boolean = this.synchronized { size == 0 }

  def bufferWrite(seg: TCPSegment) {
    this.synchronized {
      val len = seg.head.dataOffset + seg.payLoad.length
      if (len > capacity - size) {
        println("No enough space to store the segment, drop this segment")
      } else {
        buffer.enqueue(seg)
        size += len
      }
    }
  }

  def bufferRead(): TCPSegment = {
    this.synchronized {
      if (size == 0) {
        null
      } else {
        val seg = buffer.dequeue
        size -= seg.head.dataOffset + seg.payLoad.length
        seg
      }
    }
  }
  
  def bufferClean() {
    this.synchronized{
      buffer.clear
      size = 0
    }
  }
}