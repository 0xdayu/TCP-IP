package tcp

class TCPConnection(p : Int, fb : Int, sw : Int) {
  
	object State extends Enumeration  {
	  val ESTABLISHED, SYN_SENT, SYN_RECV, FIN_WAIT1, FIN_WAIT2, TIME_WAIT, CLOSE, CLOSE_WAIT, LAST_ACK, LISTEN, CLOSING = Value
	}
	var port: Int = p
	var state = State.CLOSE 
	var flowBuf: Array[Byte] = new Array[Byte](fb)
	var slidingWinow: Array[Byte] = new Array[Byte](sw)
	
	
}