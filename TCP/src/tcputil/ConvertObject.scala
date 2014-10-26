package tcputil

import tcp.{ TCPHead, TCPSegment }

object ConvertObject {
  val DefaultHeadLength = 20

  def TCPSegmentToByte(segment: TCPSegment): Array[Byte] = {
    val buf = Array.ofDim[Byte](segment.head.dataOffset)

    val head = segment.head

    buf(0) = ((head.sourcePort >> 8) & 0xff).asInstanceOf[Byte]
    buf(1) = (head.sourcePort & 0xff).asInstanceOf[Byte]

    buf(2) = ((head.destinationPort >> 8) & 0xff).asInstanceOf[Byte]
    buf(3) = (head.destinationPort & 0xff).asInstanceOf[Byte]

    buf(4) = ((head.seqNum >> 24) & 0xff).asInstanceOf[Byte]
    buf(5) = ((head.seqNum >> 16) & 0xff).asInstanceOf[Byte]
    buf(6) = ((head.seqNum >> 8) & 0xff).asInstanceOf[Byte]
    buf(7) = (head.seqNum & 0xff).asInstanceOf[Byte]

    buf(8) = ((head.ackNum >> 24) & 0xff).asInstanceOf[Byte]
    buf(9) = ((head.ackNum >> 16) & 0xff).asInstanceOf[Byte]
    buf(10) = ((head.ackNum >> 8) & 0xff).asInstanceOf[Byte]
    buf(11) = (head.ackNum & 0xff).asInstanceOf[Byte]

    buf(12) = (((((head.dataOffset / 4) << 4) & 0xf) | (head.ns & 1)) & 0xff).asInstanceOf[Byte]
    buf(13) = (((head.cwr << 8) | (head.ece << 7) | (head.urg << 6) | (head.ack << 5) | (head.psh << 4) | (head.rst << 3) | (head.syn << 2) | head.fin) & 0xff).asInstanceOf[Byte]

    buf(14) = ((head.winSize >> 8) & 0xff).asInstanceOf[Byte]
    buf(15) = (head.winSize & 0xff).asInstanceOf[Byte]

    buf(16) = ((head.checkSum >> 8) & 0xff).asInstanceOf[Byte]
    buf(17) = (head.checkSum & 0xff).asInstanceOf[Byte]

    buf(18) = ((head.urgentPointer >> 8) & 0xff).asInstanceOf[Byte]
    buf(19) = (head.urgentPointer & 0xff).asInstanceOf[Byte]

    if (head.option != null) {
      Array.copy(head.option, 0, buf, DefaultHeadLength, head.option.length)
    }

    buf ++ segment.payLoad
  }

  def byteToTCPSegment(buf: Array[Byte]): TCPSegment = {
    val segment = new TCPSegment
    val head = new TCPHead

    if (buf.length < DefaultHeadLength) {
      return null
    }

    // Big-Endian
    head.sourcePort = ((((buf(0) & 0xff) << 8) | (buf(1) & 0xff)) & 0xffff).asInstanceOf[Int]
    head.destinationPort = ((((buf(2) & 0xff) << 8) | (buf(3) & 0xff)) & 0xffff).asInstanceOf[Int]

    head.seqNum = (((((buf(4) & 0xff) << 24 | (buf(5) & 0xff) << 16) | (buf(6) & 0xff) << 8) | (buf(7) & 0xff)) & 0xffffffff).asInstanceOf[Long]
    head.ackNum = (((((buf(8) & 0xff) << 24 | (buf(9) & 0xff) << 16) | (buf(10) & 0xff) << 8) | (buf(11) & 0xff)) & 0xffffffff).asInstanceOf[Long]

    head.dataOffset = ((buf(12) >> 4) & 0xf).asInstanceOf[Int] * 4

    if (head.dataOffset < DefaultHeadLength) {
      return null
    }

    head.ns = (buf(12) & 1).asInstanceOf[Int]
    head.cwr = ((buf(13) >> 7) & 1).asInstanceOf[Int]
    head.ece = ((buf(13) >> 6) & 1).asInstanceOf[Int]
    head.urg = ((buf(13) >> 5) & 1).asInstanceOf[Int]
    head.ack = ((buf(13) >> 4) & 1).asInstanceOf[Int]
    head.psh = ((buf(13) >> 3) & 1).asInstanceOf[Int]
    head.rst = ((buf(13) >> 2) & 1).asInstanceOf[Int]
    head.syn = ((buf(13) >> 1) & 1).asInstanceOf[Int]
    head.fin = (buf(13) & 1).asInstanceOf[Int]

    head.winSize = ((((buf(14) & 0xff) << 8) | (buf(15) & 0xff)) & 0xffff).asInstanceOf[Int]

    head.checkSum = ((((buf(16) & 0xff) << 8) | (buf(17) & 0xff)) & 0xffff).asInstanceOf[Int]

    head.urgentPointer = ((((buf(18) & 0xff) << 8) | (buf(19) & 0xff)) & 0xffff).asInstanceOf[Int]

    if (head.dataOffset != DefaultHeadLength) {
      head.option = buf.slice(DefaultHeadLength, head.dataOffset)
    }

    segment.head = head
    segment.payLoad = buf.slice(head.dataOffset, buf.length)

    segment
  }
}