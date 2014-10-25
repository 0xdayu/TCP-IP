package ip

import java.net.InetAddress

class IPHead {
  var versionAndIhl: Short = _ // (Byte) four bits - version, four bits - header length
  var tos: Short = _ // (Byte) type of service
  var totlen: Int = _ // (Short) total length

  var id: Int = _ // (Short) identification
  var fragoff: Int = _ // (Short) fragment offset field

  var ttl: Short = _ // (Byte) time to live
  var protocol: Short = _ // (Byte) protocol
  var check: Int = _ // (Short) checksum

  var saddr: InetAddress = _ // source address
  var daddr: InetAddress = _ // dest address	

  var option: Array[Byte] = _ // option
}