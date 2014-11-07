package tcputil

class RecvBuffer(capacity: Int, sliding: Int) {
  var recvBuf: Array[Byte] = new Array[Byte](0)

  // Begin, End
  var linkList: Array[(Int, Int, Array[Byte])] = new Array[(Int, Int, Array[Byte])](0)
  var linkListSize = 0

  var available: Int = capacity
  var slide: Int = sliding

  def read(size: Int): Array[Byte] = {
    this.synchronized {
      val realLen = math.min(recvBuf.length, size)
      val result = recvBuf.slice(0, realLen)
      recvBuf = recvBuf.slice(realLen, recvBuf.length)
      available += realLen

      result
    }
  }

  // return to move how many bytes
  def write(off: Int, buf: Array[Byte]): Int = {
    this.synchronized {
      // TODO: sliding window may change
      if (buf.length == 0) {
        return 0
      }

      if (slide <= linkListSize) {
        return 0
      }

      // off will not larger than slide
      val realLen = math.min(slide - off, buf.length)
      
      if (realLen == 0) {
        return 0
      }

      val data = buf.slice(0, realLen)
      var originSize = realLen

      val begin = off
      val end = off + realLen

      var addNode = (begin, end, data)

      var templinkList: Array[(Int, Int, Array[Byte])] = new Array[(Int, Int, Array[Byte])](0)

      for (node <- linkList) {
        if (addNode._2 <= node._1) {
          if (addNode._2 == node._1) {
            addNode = (addNode._1, node._2, addNode._3 ++ node._3)
          } else {
            templinkList = templinkList :+ addNode
            addNode = node
          }
        } else if (addNode._1 >= node._2) {
          if (addNode._1 == node._2) {
            addNode = (node._1, addNode._2, node._3 ++ addNode._3)
          } else {
            templinkList = templinkList :+ node
          }
        } else {
          if (addNode._1 >= node._1 && addNode._2 <= node._2 ){
            addNode = node
            originSize = 0
          } else if (addNode._1 <= node._1 && addNode._2 >= node._2){
            // addNode = addNode
          } else if (addNode._1 <= node._1 && addNode._2 <= node._2) {
            addNode = (addNode._1, node._2, addNode._3.slice(0, node._1 - addNode._1) ++ node._3)
            originSize -= (addNode._2 - node._1)
          } else {
            addNode = (node._1, addNode._2, node._3.slice(0, addNode._1 - node._1) ++ addNode._3)
            originSize -= (node._2 - addNode._1)
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

      templength
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

  def getAvailable(): Int = {
    this.synchronized {
      available
    }
  }
}