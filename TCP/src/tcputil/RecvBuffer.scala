package tcputil

class RecvBuffer(capacity: Int, sliding: Int) {
  var recvBuf: Array[Byte] = _

  // Begin, End
  var linkList: Array[(Int, Int, Array[Byte])] = new Array[(Int, Int, Array[Byte])](0)
  var linkListSize = 0

  var available: Int = capacity
  var slide: Int = sliding

  def read(size: Int): Array[Byte] = {
    val realLen = math.min(recvBuf.length, size)
    val result = recvBuf.slice(0, realLen)
    recvBuf = recvBuf.slice(realLen, recvBuf.length)
    available += realLen

    result
  }

  // first indicates to move how many bytes, second is the byte we write
  def write(off: Int, buf: Array[Byte]): (Int, Int) = {
    // TODO: sliding window may change
    val realLen = math.min(slide - linkListSize, buf.length)
    val data = buf.slice(0, realLen)
    var originSize = realLen

    val begin = off
    val end = off + realLen

    var addNode = (begin, end, data)

    var templinkList: Array[(Int, Int, Array[Byte])] = new Array[(Int, Int, Array[Byte])](0)

    for (node <- linkList) {
      if (addNode._2 < node._1) {
        if (addNode._2 == node._1 - 1) {
          addNode = (addNode._1, node._2, addNode._3 ++ node._3)
        } else {
          templinkList = templinkList :+ addNode
          addNode = node
        }
      } else if (addNode._1 > node._2) {
        if (addNode._1 == node._2 + 1) {
          addNode = (node._1, addNode._2, node._3 ++ addNode._3)
        } else {
          templinkList = templinkList :+ node
        }
      } else {
        if (addNode._1 <= node._1 && addNode._2 <= node._2) {
          addNode = (addNode._1, node._2, addNode._3.slice(0, node._1 - addNode._1) ++ node._3)
          originSize -= (node._1 - addNode._2)
        } else {
          addNode = (node._1, addNode._2, node._3.slice(0, addNode._1 - node._1) ++ addNode._3)
          originSize -= (addNode._1 - node._2)
        }
      }
    }
    templinkList = templinkList :+ addNode

    linkList = templinkList

    linkListSize += originSize

    available -= originSize

    var templength = 0

    if (linkList(0)._1 == 0) {
      recvBuf = recvBuf ++ linkList(0)._3
      templength = linkList(0)._3.length
      linkListSize -= linkList(0)._3.length
      linkList = linkList.slice(1, linkList.length)
    }

    (templength, realLen)
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