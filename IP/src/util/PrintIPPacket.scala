package util

import ip.{ IPPacket, IPHead }

object PrintIPPacket {
  def printIPPacket(packet: IPPacket, isHeadBinary: Boolean, isPayloadBinary: Boolean) {
    println("========================IP Head=======================")

    if (isHeadBinary) {
      printIPHeadAsBinary(packet.head)
    } else {
      printIPHeadAsString(packet.head)
    }

    println("========================IP Payload=======================")

    if (isPayloadBinary) {
      printBinary(packet.payLoad)
    } else {
      println(new String(packet.payLoad.map(_.toChar)))
    }

    println("=========================================================")
  }

  def printIPHeadAsBinary(head: IPHead) {
    val headBytes = ConvertObject.headToByte(head)
    printBinary(headBytes)
  }

  def printBinary(bArray: Array[Byte]) {
    var count = 0
    for (b <- bArray) {
      if (count == 4) {
        count = 0
        println
      }
      print(Integer.toBinaryString(b & 0xff) + " | ")
      count += 1
    }
  }

  def printIPHeadAsString(head: IPHead) {
    println("Version:\t" + ((head.versionAndIhl >> 8) & 0xff).asInstanceOf[Int])
    println("Header length:\t" + (head.versionAndIhl & 0xff).asInstanceOf[Int] * 4)
    println("Type of service:\t" + head.tos)
    println("Total length:\t" + head.totlen)

    println("Identification:\t" + head.id)
    println("Don't Fragment:\t" + (head.fragoff & (1 << 14)))
    println("More Fragments:\t" + (head.fragoff & (1 << 13)))
    println("Fragment Offset:\t" + (head.fragoff & ~(1 << 14) & ~(1 << 13)) * 8)

    println("Time to live:\t" + head.ttl)
    println("The protocol number:\t" + head.protocol)
    println("The check sum:\t" + head.check)

    println("Source address:\t" + head.saddr.getHostAddress())
    println("Destination address:\t" + head.daddr.getHostAddress())
  }
}