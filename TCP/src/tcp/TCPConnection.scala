package tcp

import tcputil.TCPState

class TCPConnection(p : Int, fb : Int, sw : Int) {
  
	var port: Int = p
	var state = TCPState.CLOSE 
	var flowBuf: Array[Byte] = new Array[Byte](fb)
	var slidingWinow: Array[Byte] = new Array[Byte](sw)
	
	def setState(s : TCPState.Value) {
	  state = s
	}
}