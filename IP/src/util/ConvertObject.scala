package util

import ip.{ IPHead, RIP }
import java.net.InetAddress

object ConvertObject {

  // helper function
  def headToByte(head: IPHead): Array[Byte] = {
    val len: Int = headLen(ConvertNumber.shortToUint8(head.versionAndIhl))
    val buf = Array.ofDim[Byte](len)

    // Big-Endian
    buf(0) = ConvertNumber.shortToUint8(head.versionAndIhl)

    buf(1) = ConvertNumber.shortToUint8(head.tos)

    buf(2) = ((head.totlen >> 8) & 0xff).asInstanceOf[Byte]
    buf(3) = (head.totlen & 0xff).asInstanceOf[Byte]

    buf(4) = ((head.id >> 8) & 0xff).asInstanceOf[Byte]
    buf(5) = (head.id & 0xff).asInstanceOf[Byte]

    buf(6) = ((head.fragoff >> 8) & 0xff).asInstanceOf[Byte]
    buf(7) = (head.fragoff & 0xff).asInstanceOf[Byte]

    buf(8) = ConvertNumber.shortToUint8(head.ttl)

    buf(9) = ConvertNumber.shortToUint8(head.protocol)

    buf(10) = ((head.check >> 8) & 0xff).asInstanceOf[Byte]
    buf(11) = (head.check & 0xff).asInstanceOf[Byte]

    toByteAddr(buf, 12, head.saddr)
    toByteAddr(buf, 16, head.daddr)

    buf
  }

  def byteToHead(buf: Array[Byte]): IPHead = {
    val head = new IPHead

    // Big-Endian
    head.versionAndIhl = ConvertNumber.uint8ToShort(buf(0))

    if (headLen(buf(0)) != buf.length) {
      return null
    }

    head.tos = ConvertNumber.uint8ToShort(buf(1))

    head.totlen = ((((buf(2) & 0xff) << 8) | (buf(3) & 0xff)) & 0xffff).asInstanceOf[Int]

    head.id = ((((buf(4) & 0xff) << 8) | (buf(5) & 0xff)) & 0xffff).asInstanceOf[Int]

    head.fragoff = ((((buf(6) & 0xff) << 8) | (buf(7) & 0xff)) & 0xffff).asInstanceOf[Int]

    head.ttl = ConvertNumber.uint8ToShort(buf(8))

    head.protocol = ConvertNumber.uint8ToShort(buf(9))

    head.check = ((((buf(10) & 0xff) << 8) | (buf(11) & 0xff)) & 0xffff).asInstanceOf[Int]

    head.saddr = toInetAddr(buf, 12)

    head.daddr = toInetAddr(buf, 16)

    head
  }

  def RIPToByte(rip: RIP): Array[Byte] = {
    val buf = Array.ofDim[Byte](4 + rip.numEntries * 8)

    // Big-Endian
    buf(0) = ((rip.command >> 8) & 0xff).asInstanceOf[Byte]
    buf(1) = (rip.command & 0xff).asInstanceOf[Byte]

    buf(2) = ((rip.numEntries >> 8) & 0xff).asInstanceOf[Byte]
    buf(3) = (rip.numEntries & 0xff).asInstanceOf[Byte]

    var i = 4
    for (entry <- rip.entries) {
      buf(i) = ((entry._1 >> 24) & 0xff).asInstanceOf[Byte]
      buf(i + 1) = ((entry._1 >> 16) & 0xff).asInstanceOf[Byte]
      buf(i + 2) = ((entry._1 >> 8) & 0xff).asInstanceOf[Byte]
      buf(i + 3) = (entry._1 & 0xff).asInstanceOf[Byte]
      toByteAddr(buf, i + 4, entry._2)
      i += 8
    }

    buf
  }

  def byteToRIP(buf: Array[Byte]): RIP = {
    if (buf.length < 4) {
      return null
    }

    val rip = new RIP

    // Big-Endian
    rip.command = ((((buf(0) & 0xff) << 8) | (buf(1) & 0xff)) & 0xffff).asInstanceOf[Int]
    rip.numEntries = ((((buf(2) & 0xff) << 8) | (buf(3) & 0xff)) & 0xffff).asInstanceOf[Int]

    // not equal
    if (buf.length != 4 + rip.numEntries * 8) {
      return null
    }

    val array = Array.ofDim[(Int, InetAddress)](rip.numEntries)
    var i = 0

    for (count <- Range(4, 4 + rip.numEntries * 8, 8)) {
      val part1 = (((buf(count) & 0xff) << 24) | ((buf(count + 1) & 0xff) << 16)).asInstanceOf[Int]
      val part2 = (((buf(count + 2) & 0xff) << 8) | (buf(count + 3) & 0xff)).asInstanceOf[Int]

      array(i) = (part1 | part2, toInetAddr(buf, count + 4))
      i += 1
    }

    rip.entries = array

    rip
  }

  def toByteAddr(buf: Array[Byte], i: Int, addr: InetAddress) {
    var count = i
    for (b <- addr.getAddress()) {
      buf(count) = b
      count += 1
    }
  }

  def toInetAddr(buf: Array[Byte], i: Int): InetAddress = {
    val b = Array.ofDim[Byte](4)

    b(0) = buf(i)
    b(1) = buf(i + 1)
    b(2) = buf(i + 2)
    b(3) = buf(i + 3)

    val addr = InetAddress.getByAddress(b)
    addr
  }

  def headLen(b: Byte): Int = (b & 0xf).asInstanceOf[Int] * 4
}