package iputil

import scala.collection.mutable.Queue
import ip.IPPacket

class FIFOBuffer(capacity: Int) {
  val buffer = new Queue[IPPacket]
  var size = 0

  def getCapacity: Int = capacity

  def getSize: Int = this.synchronized { size }

  def getAvailable: Int = this.synchronized { capacity - size }

  def isFull: Boolean = this.synchronized { capacity == size }

  def isEmpty: Boolean = this.synchronized { size == 0 }

  def bufferWrite(pkt: IPPacket) {
    this.synchronized {
      val len = ConvertObject.headLen(ConvertNumber.shortToUint8(pkt.head.versionAndIhl)) + pkt.payLoad.length
      if (len > capacity - size) {
        println("No enough space to store the packet, drop this packet")
      } else {
        buffer.enqueue(pkt)
        size += len
      }
    }
  }

  def bufferRead(): IPPacket = {
    this.synchronized {
      if (size == 0) {
        null
      } else {
        val pkt = buffer.dequeue
        size -= ConvertObject.headLen(ConvertNumber.shortToUint8(pkt.head.versionAndIhl)) + pkt.payLoad.length
        pkt
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