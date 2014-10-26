package iputil

object IPSum {
  def ipsum(packet: Array[Byte]): Long = {
    var sum: Long = 0
    var len: Int = packet.length
    var i: Int = 0

    while (len > 1) {
      sum += ((packet(i) << 8 & 0xff00) | (packet(i + 1) & 0xff)).asInstanceOf[Long]

      i += 2
      len -= 2
    }

    if (len == 1) {
      sum += (packet(i) & 0xff).asInstanceOf[Long]
    }

    sum = (sum >> 16) + (sum & 0xffff)
    sum += (sum >> 16)
    ~sum
  }
}