package tcp

import exception._
import java.net.InetAddress
import java.util.BitSet
import scala.collection.mutable.HashMap

class TCP {
	// file descriptor, 0 - input, 1 - output, 2 - error
	// start from 3 to 65535 (2^16 - 1) or less
	val socketArray = new BitSet
	val socketHashMap = new HashMap
	
	// port number, start from 1024 to 65535 (2^16 - 1)
	val portArray = new BitSet
  
	def virSocket(): Int = {
	  for (i <- Range(3, 65536)) {
	    if (!socketArray.get(i)) {
	      i
	    }
	  }
	  
	  throw new UnboundSocketException
	}
	
	def virBind(socket: Int, addr: InetAddress, port: Int) {
	  
	}
	
	def virListen(socket: Int) {
	  
	}
	
	def virConnect(socket: Int, addr: InetAddress, port: Int) {
	  
	}
	
	def virAccept(socket: Int):TCPConnection = {
	  
	}
	
	def virRead(socket: Int): Array[Byte] = {
	  
	}
	
	def virWrite(socket: Int, buf: Array[Byte]) {
	  
	}
	
	def virShutdown(socket: Int, sdType: Int) {
	  
	}
	
	def virClose(socket: Int) {
	  
	}
}