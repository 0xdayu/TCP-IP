package ip

import java.net.InetAddress

class RIP {
	var command: Int = _ // (Short) command: 1 - request, 2 - response
	var numEntries: Int = _ // (Short) number of entries
	var entries: Array[(Int, InetAddress)] = _ // cost and address
}