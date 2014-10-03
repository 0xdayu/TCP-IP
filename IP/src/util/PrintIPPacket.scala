package util

import ip.{ IPPacket, IPHead, RIP }
import java.text.DecimalFormat

object PrintIPPacket {
  def printIPPacket(packet: IPPacket, isHeadBinary: Boolean, isPayloadBinary: Boolean, isRIP: Boolean) {
    println("========================IP Head==========================")

    if (isHeadBinary) {
      printIPHeadAsBinary(packet.head)
    } else {
      printIPHeadAsString(packet.head)
    }

    println("========================IP Payload=======================")

    if (isPayloadBinary) {
      printBinary(packet.payLoad)
    } else {
      if (isRIP) {
        printRIPAsString(ConvertObject.byteToRIP(packet.payLoad))
      } else {
        if (packet.payLoad.isEmpty) {
          println("Nothing in payload")
        } else {
          println(new String(packet.payLoad.map(_.toChar)))
        }
      }
    }

    println("=========================================================")
  }

  def printIPHeadAsBinary(head: IPHead) {
    val headBytes = ConvertObject.headToByte(head)
    printBinary(headBytes)
  }

  def printBinary(bArray: Array[Byte]) {
    var count = 0
    val numFormat = new DecimalFormat("00000000")
    for (b <- bArray) {
      if (count == 4) {
        count = 0
        println
      }
      print(numFormat.format((Integer.valueOf(Integer.toBinaryString(b & 0xff)))) + "  |  ")
      count += 1
    }
    println
  }

  def printIPHeadAsString(head: IPHead) {
    println("Version:\t\t" + ((head.versionAndIhl >> 4) & 0xf).asInstanceOf[Int])
    println("Header length:\t\t" + (head.versionAndIhl & 0xf).asInstanceOf[Int] * 4)
    println("Type of service:\t" + head.tos)
    println("Total length:\t\t" + head.totlen)

    println("Identification:\t\t" + head.id)
    println("Don't Fragment:\t\t" + (head.fragoff & (1 << 14)))
    println("More Fragments:\t\t" + (head.fragoff & (1 << 13)))
    println("Fragment Offset:\t" + (head.fragoff & ~(1 << 14) & ~(1 << 13)) * 8)

    println("Time to live:\t\t" + head.ttl)
    println("The protocol number:\t" + head.protocol)
    println("The check sum:\t\t" + head.check)

    println("Source address:\t\t" + head.saddr.getHostAddress)
    println("Destination address:\t" + head.daddr.getHostAddress)
  }

  def printRIPAsString(rip: RIP) {
    println("Command:\t" + rip.command)
    println("Number of entries:\t" + rip.numEntries)
    println("\tNum\tCost\tAddress")
    for (i <- Range(0, rip.numEntries)) {
      println("\t" + i + "\t" + rip.entries(i)._1 + "\t" + rip.entries(i)._2.getAddress)
    }
  }
}