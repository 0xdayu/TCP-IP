package util

import scala.collection.mutable.Queue
import scala.collection.mutable.ArrayBuffer

class FIFOBuffer(capacity: Int) {
	val buffer = new Queue[Byte]
	val size = 0
	
	def getCapacity: Int = capacity
	
	def getSize: Int = size
	
	def getAvailable: Int = capacity - size
	
	def isFull: Boolean = capacity == size
	
	def isEmpty: Boolean = size == 0
	
	def bufferWrite(buf: ArrayBuffer[Byte]): Int = {
	  val len = math.min(buf.length, getAvailable)
	  for (i <- Range(0, len - 1)) {
	    buf(i) = buffer.front
	  }
	  len
	}
	
	def bufferRead(buf: ArrayBuffer[Byte]): Int = {
	  val len = math.min(buf.length, getSize)
	  for (i <- Range(0, len - 1)) {
	    buffer enqueue buf(0) 
	  }
	  len
	} 
}