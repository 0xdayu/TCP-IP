package tcp

class TCPHead {
	var sourcePort: Int = _ // (Short) source port
	var destinationPort: Int = _ // (Short) destination port
	
	var seqNum: Long = _ // (Int) sequence number
	var ackNum: Long = _ // (Int) acknowledgment number
	
	var dataOffset: Int = _ // (four bits) data offset
	
	var ns: Int = _ // (one bit) ECN-nonce concealment protection
	var cwr: Int = _ // (one bit) Congestion Window Reduced
	var ece: Int = _ // (one bit) ECN-Echo
	var urg: Int = _ // (one bit) Urgent pointer
	var ack: Int = _ // (one bit) Acknowledgment
	var psh: Int = _ // (one bit) push function
	var rst: Int = _ // (one bit) reset the connection
	var syn: Int = _ // (one bit) synchronize sequence numbers
	var fin: Int = _ // (one bit) no more data from sender
	
	var winSize: Int = _ // (Short) window size
	
	var checkSum: Int = _ // (Short) checksum
	var urgentPointer: Int = _ // (Short) urgent pointer
	
	var option: Array[Byte] = _ // option
}