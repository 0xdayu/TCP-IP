package tcp

class TCPConnection {
	object State extends Enumeration {
	  val ESTABLISHED, SYN_SENT, SYN_RECV, FIN_WAIT1, FIN_WAIT2, TIME_WAIT, CLOSE, CLOSE_WAIT, LAST_ACK, LISTEN, CLOSING = Value
	}
	var port: Int = _
	var state: Int = _
	var flowBuf: Array[Byte] = _
	var slidingWinow: Array[Byte] = _
	
}