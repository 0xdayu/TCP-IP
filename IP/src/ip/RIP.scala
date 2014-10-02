package ip

import java.net.InetAddress

class RIP {
	var afId: Int = _ // (Short) address family identifier
	var tag: Int = _ // (Short) route tag
	var IPAddr: InetAddress = _ // IP address
	var mask: Long = _ // (Int) subnet mask
	var nextHop: InetAddress = _ // next hop
	var metric: Long = _ // (Int) metric
}