package ip

import java.net.InetAddress

class IPHead extends Serializable {
  var version: Int = _ // version
  var ihl: Int = _ // header length

  var tos: Byte = _ // type of service
  var totlen: Short = _ // total length
  var id: Short = _ // identification
  var fragoff: Short = _ // fragment offset field
  var ttl: Byte = _ // time to live
  var protocol: Byte = _ // protocol
  var check: Short = _ // checksum
  var saddr: InetAddress = _ // source address
  var daddr: InetAddress = _ // dest address	
}