package tcp

class TCPConnection {
	var port: Int = _
	var state: Int = _
	var flowBuf: Array[Byte] = _
	var slidingWinow: Array[Byte] = _
	
}