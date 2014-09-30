package util

import scala.collection.mutable.Queue
import scala.collection.mutable.ArrayBuffer
import ip.IPPacket

class FIFOBuffer(capacity: Int) {
	val buffer = new Queue[IPPacket]
	var size = 0
	
	def getCapacity: Int = capacity
	
	def getSize: Int = size
	
	def getAvailable: Int = capacity - size
	
	def isFull: Boolean = capacity == size
	
	def isEmpty: Boolean = size == 0
	
	def bufferWrite(pkt: IPPacket): Boolean = {
	  val len = ConvertObject.headLen(pkt.head.versionAndIhl) + pkt.payLoad.length
	  if (len > getAvailable) {
	    println("No enough space to store the packet, drop this packet")
	    false
	  } else {
	    buffer.enqueue(pkt)
	    size += len
	    true
	  }
	}
	
	def bufferRead(): IPPacket = {
	  if (isEmpty) {
	    null
	  } else {
	    val pkt = buffer.dequeue
	    size -= ConvertObject.headLen(pkt.head.versionAndIhl) + pkt.payLoad.length
	    pkt
	  }
	} 
}