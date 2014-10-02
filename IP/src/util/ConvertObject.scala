package util

import ip.{ IPHead, RIP }
import java.net.InetAddress

object ConvertObject {
  val RIPLen = 20

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

    head.tos = ConvertNumber.uint8ToShort(buf(1))

    head.totlen = (((buf(2) << 8) | buf(3)) & 0xffff).asInstanceOf[Int]

    head.id = (((buf(4) << 8) | buf(5)) & 0xffff).asInstanceOf[Int]

    head.fragoff = (((buf(6) << 8) | buf(7)) & 0xffff).asInstanceOf[Int]

    head.ttl = ConvertNumber.uint8ToShort(buf(8))

    head.protocol = ConvertNumber.uint8ToShort(buf(9))

    head.check = (((buf(10) << 8) | buf(11)) & 0xffff).asInstanceOf[Int]

    head.saddr = toInetAddr(buf, 12)

    head.daddr = toInetAddr(buf, 16)

    head
  }

  def RIPToByte(rip: RIP): Array[Byte] = {
    val buf = Array.ofDim[Byte](RIPLen)

    // Big-Endian
    buf(0) = ((rip.afId >> 8) & 0xff).asInstanceOf[Byte]
    buf(1) = (rip.afId & 0xff).asInstanceOf[Byte]

    buf(2) = ((rip.tag >> 8) & 0xff).asInstanceOf[Byte]
    buf(3) = (rip.tag & 0xff).asInstanceOf[Byte]

    toByteAddr(buf, 4, rip.IPAddr)

    buf(8) = ((rip.mask >> 24) & 0xff).asInstanceOf[Byte]
    buf(9) = ((rip.mask >> 16) & 0xff).asInstanceOf[Byte]
    buf(10) = ((rip.mask >> 8) & 0xff).asInstanceOf[Byte]
    buf(11) = (rip.mask & 0xff).asInstanceOf[Byte]

    toByteAddr(buf, 12, rip.nextHop)

    buf(16) = ((rip.metric >> 24) & 0xff).asInstanceOf[Byte]
    buf(17) = ((rip.metric >> 16) & 0xff).asInstanceOf[Byte]
    buf(18) = ((rip.metric >> 8) & 0xff).asInstanceOf[Byte]
    buf(19) = (rip.metric & 0xff).asInstanceOf[Byte]

    buf
  }

  def byteToRIP(buf: Array[Byte]): RIP = {
    val rip = new RIP

    // Big-Endian
    rip.afId = (((buf(0) << 8) | buf(1)) & 0xffff).asInstanceOf[Int]
    rip.tag = (((buf(2) << 8) | buf(3)) & 0xffff).asInstanceOf[Int]

    rip.IPAddr = toInetAddr(buf, 4)
    val part1 = (((buf(8) << 24) | (buf(9) << 16)) & 0xffff).asInstanceOf[Long]
    val part2 = (((buf(10) << 8) | buf(11)) & 0xffff).asInstanceOf[Long]
    rip.mask = part1 | part2

    rip.nextHop = toInetAddr(buf, 12)

    val part3 = (((buf(16) << 24) | (buf(17) << 16)) & 0xffff).asInstanceOf[Long]
    val part4 = (((buf(18) << 8) | buf(19)) & 0xffff).asInstanceOf[Long]
    rip.metric = part3 | part4

    rip
  }

  def toByteAddr(buf: Array[Byte], i: Int, addr: InetAddress) {
    var count = i
    for (b <- addr.getAddress()) {
      buf(i) = b
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