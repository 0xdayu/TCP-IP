package util

object IPSum {
  def ipsum(packet: Array[Byte]) = {
    var sum: Long = 0
    var len: Int = packet.length
    var i: Int = 0
    
    while (len > 1) {
      sum += packet(i) << 8 + packet(i + 1)
      i += 2
      len -= 2
    }
    
    // TODO
    if (len == 1) {
      sum += packet(i)
    }
    
    sum = (sum >> 16) + (sum & 0xffff)
    sum += (sum >> 16)
    ~sum
  }
}