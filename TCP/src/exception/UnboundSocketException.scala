package exception

class UnboundSocketException extends Exception {
	def getMessage(): String = {
	  "The socket from 3 to 65535 has been used and no enough socket number"
	}
}