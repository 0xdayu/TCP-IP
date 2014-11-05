package tcputil

import java.util.ArrayList

class SendBuffer(capacity: Int, sliding: Int) {
  var writeBuf: Array[Byte] = _
  var sendBuf: Array[Byte] = _

  var available: Int = capacity
  var slide: Int = sliding

  def write(buf: Array[Byte]): Int = {
    this.synchronized {
      val realLen = math.min(buf.length, available)
      writeBuf ++= buf.slice(0, realLen)
      available -= realLen

      realLen
    }
  }

  def send(size: Int): Array[Byte] = {
    this.synchronized {
      // maybe slide < sendBuf.length
      if (slide <= sendBuf.length) {
        new Array[Byte](0)
      } else {
        val realLen = math.min(math.min(size, writeBuf.length), slide - sendBuf.length)
        val pending = writeBuf.slice(0, realLen)
        writeBuf = writeBuf.slice(realLen, writeBuf.length)
        sendBuf ++= pending

        pending
      }
    }
  }

  def checkAck(len: Int) {
    this.synchronized {
      if (len != 0) {
        sendBuf = sendBuf.slice(len, sendBuf.length)
        available += len
      }
    }
  }

  def setSliding(newSliding: Int) {
    this.synchronized {
      slide = newSliding
    }
  }

  def getSliding() {
    this.synchronized {
      slide
    }
  }
}