package util

import ip.{ IPPacket, IPHead }
import scala.collection.mutable.LinkedHashMap

object IPPacketFragmentation {
  val MaxPacket = 64 * 1024

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

  def reassemblePacket(fragmentHashMap: LinkedHashMap[Int, (Long, Int, Int, Array[Byte])], incomingPacket: IPPacket): IPPacket = {
    if (fragmentHashMap.contains(incomingPacket.head.id)) {
      var (time, size, totalSize, array) = fragmentHashMap.get(incomingPacket.head.id).getOrElse(null)

      val offset = (incomingPacket.head.fragoff & (~(1 << 13))) * 8
      val len = incomingPacket.head.totlen - (incomingPacket.head.versionAndIhl & 0xf).asInstanceOf[Int] * 4
      Array.copy(incomingPacket.payLoad, 0, array, offset, len)

      size += len
      // last packet
      if (((incomingPacket.head.fragoff >> 13) & 1) == 0) {
        totalSize = offset + len
      }

      if (size == totalSize) {
        // assemble successfully
        fragmentHashMap.remove(incomingPacket.head.id)

        val newPacket = new IPPacket
        val newHead = new IPHead
        newHead.versionAndIhl = incomingPacket.head.versionAndIhl
        newHead.tos = incomingPacket.head.tos
        newHead.ttl = incomingPacket.head.ttl
        newHead.id = incomingPacket.head.id
        newHead.protocol = incomingPacket.head.protocol
        newHead.saddr = incomingPacket.head.saddr
        newHead.daddr = incomingPacket.head.daddr
        newHead.check = 0
        newHead.fragoff = 0
        newHead.totlen = totalSize + (incomingPacket.head.versionAndIhl & 0xf).asInstanceOf[Int] * 4

        newPacket.head = newHead
        newPacket.payLoad = array.slice(0, totalSize)
        return newPacket
      } else {
        // update
        fragmentHashMap.update(incomingPacket.head.id, (time, size, totalSize, array))
      }
    } else {
      // initial
      val array = new Array[Byte](MaxPacket)
      val offset = (incomingPacket.head.fragoff & (~(1 << 13))) * 8
      val len = incomingPacket.head.totlen - (incomingPacket.head.versionAndIhl & 0xf).asInstanceOf[Int] * 4
      Array.copy(incomingPacket.payLoad, 0, array, offset, len)
      fragmentHashMap.put(incomingPacket.head.id, (System.currentTimeMillis, len, -1, array))
    }

    null
  }
}