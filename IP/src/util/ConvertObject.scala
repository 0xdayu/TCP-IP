package util

import ip.IPHead
import java.net.InetAddress

object ConvertObject {
  // helper function
  def headToByte(head: IPHead): Array[Byte] = {
    val len: Int = headLen(head.versionAndIhl)
    val buf = new Array[Byte](len)
    
    // Big-Endian
    buf(0) = head.versionAndIhl
    
    buf(1) = head.tos
    
    buf(2) = ((head.totlen >> 8) & 0xff).asInstanceOf[Byte]
    buf(3) = (head.totlen & 0xff).asInstanceOf[Byte]
    
    buf(4) = ((head.id >> 8) & 0xff).asInstanceOf[Byte]
    buf(5) = (head.id & 0xff).asInstanceOf[Byte]
    
    buf(6) = ((head.fragoff >> 8) & 0xff).asInstanceOf[Byte]
    buf(7) = (head.fragoff & 0xff).asInstanceOf[Byte]
    
    buf(8) = head.ttl
    
    buf(9) = head.protocol
    
    buf(10) = ((head.check >> 8) & 0xff).asInstanceOf[Byte]
    buf(11) = (head.check & 0xff).asInstanceOf[Byte]
    
    toByteAddr(buf, 12, head.saddr)
    toByteAddr(buf, 16, head.daddr)
    
    buf
  }
  
  def byteToHead(buf: Array[Byte]): IPHead = {
    val head = new IPHead
    
    // Big-Endian
    head.versionAndIhl = buf(0)
    
    head.tos = buf(1)
    
    head.totlen = ((buf(2) << 8) & buf(3)).asInstanceOf[Short]
    
    head.id = ((buf(4) << 8) & buf(5)).asInstanceOf[Short]
    
    head.fragoff = ((buf(6) << 8) & buf(7)).asInstanceOf[Short]
    
    head.ttl = buf(8)
    
    head.protocol = buf(9)
    
    head.check = ((buf(10) << 8) & buf(11)).asInstanceOf[Short]
    
    head.saddr = toInetAddr(buf, 12)
    
    head.daddr = toInetAddr(buf, 16)
    
    head
  }
  
  def toByteAddr(buf: Array[Byte], i: Int, addr: InetAddress) {
    var count = i
    for (b <- addr.getAddress()) {
      buf(i) = b
      count += 1
    }
  }
  
  def toInetAddr(buf: Array[Byte], i: Int): InetAddress = {
    val b = new Array[Byte](4)
    
    b(0) = buf(i)
    b(1) = buf(i + 1)
    b(2) = buf(i + 2)
    b(3) = buf(i + 3)
    
    val addr = InetAddress.getByAddress(b)
    addr
  }
  
  def headLen(b: Byte): Int = (b & 0xf).asInstanceOf[Int]
}