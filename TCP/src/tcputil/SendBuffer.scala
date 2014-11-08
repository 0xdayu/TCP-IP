package tcputil

import java.util.ArrayList

class SendBuffer(capacity: Int, sliding: Int) {
  var writeBuf: Array[Byte] = new Array[Byte](0)
  var sendBuf: Array[Byte] = new Array[Byte](0)

  var available: Int = capacity
  var slide: Int = sliding

  def write(buf: Array[Byte]): Int = {
    this.synchronized {
      if (available == 0) {
        this.wait
      }
      val realLen = math.min(buf.length, available)
      writeBuf ++= buf.slice(0, realLen)
      available -= realLen

      realLen
    }
  }

  def read(size: Int): Array[Byte] = {
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

  def removeFlightData(len: Int) {
    this.synchronized {
      if (len != 0) {
        if (available == 0) {
          this.notify
        }
        if (len <= sendBuf.length) {
          sendBuf = sendBuf.slice(len, sendBuf.length)
          available += len
        } else {
          // need to remove bytes more than sent bytes
          available += sendBuf.length
          sendBuf = new Array[Byte](0)
        }
      }
    }
  }

  def getSendLength(): Int = {
    this.synchronized {
      sendBuf.length
    }
  }

  def setSliding(newSliding: Int) {
    this.synchronized {
      slide = newSliding
    }
  }

  def getSliding(): Int = {
    this.synchronized {
      slide
    }
  }
}