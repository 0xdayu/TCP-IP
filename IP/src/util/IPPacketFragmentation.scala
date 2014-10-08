package util

import ip.{ IPPacket, IPHead }

object IPPacketFragmentation {

  def fragment(packet: IPPacket, mtu: Int): Array[IPPacket] = {
    // check Don't fragment bit
    if ((packet.head.fragoff & (1 << 14)) == 1 && packet.head.totlen > mtu) {
      return null
    }

    val ihl = (packet.head.versionAndIhl & 0xf).asInstanceOf[Int] * 4
    val totalContentLength = packet.head.totlen - ihl
    if (packet.head.totlen > mtu) {
      val offset: Int = (mtu - ihl) / 8
      val packetFragmentationSize = 8 * offset
      // Total Packet Fragmentation
      val packetFragmentationNumer = math.ceil(totalContentLength * 1.0 / packetFragmentationSize).asInstanceOf[Int]

      val packetFragmentationArray = new Array[IPPacket](packetFragmentationNumer)

      var currentOffset = 0

      for (i <- Range(0, packetFragmentationNumer)) {
        val newPacket = new IPPacket
        val newHead = new IPHead
        newHead.versionAndIhl = packet.head.versionAndIhl
        newHead.tos = packet.head.tos
        newHead.ttl = packet.head.ttl
        newHead.id = packet.head.id
        newHead.protocol = packet.head.protocol
        newHead.saddr = packet.head.saddr
        newHead.daddr = packet.head.daddr

        // totlen, fragoff, checksum
        if (i != (packetFragmentationNumer - 1)) {
          newHead.totlen = packetFragmentationSize + ihl
          newHead.fragoff = (1 << 13) + currentOffset / 8

          newPacket.head = newHead
          newPacket.payLoad = packet.payLoad.slice(currentOffset, currentOffset + packetFragmentationSize)
        } else {
          // Last Fragmentation
          newHead.totlen = totalContentLength - currentOffset + ihl
          newHead.fragoff = currentOffset / 8

          newPacket.head = newHead
          // large than the length (ignore)
          newPacket.payLoad = packet.payLoad.slice(currentOffset, currentOffset + packetFragmentationSize)
        }

        currentOffset = currentOffset + packetFragmentationSize
        packetFragmentationArray(i) = newPacket
      }
      packetFragmentationArray
    } else {
      Array[IPPacket](packet)
    }
  }
}