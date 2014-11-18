package tcputil

import java.util.concurrent.Semaphore

class RecvBuffer(capacity: Int) {
  var recvBuf: Array[Byte] = new Array[Byte](0)

  // Begin, End
  var linkList: Array[(Int, Int, Array[Byte])] = new Array[(Int, Int, Array[Byte])](0)

  var slide: Int = capacity

  val semaphoreCheckAvailalbe = new Semaphore(0)

  var busy: Boolean = false

  def read(size: Int): Array[Byte] = {
    this.synchronized {
      if (recvBuf.length == 0) {
        this.wait
      }
      val realLen = math.min(recvBuf.length, size)
      val result = recvBuf.slice(0, realLen)
      recvBuf = recvBuf.slice(realLen, recvBuf.length)

      slide += realLen

      if (slide == capacity && busy) {
        this.semaphoreCheckAvailalbe.release
      }

      result
    }
  }

  // return to move how many bytes
  def write(off: Int, buf: Array[Byte]): Int = {
    this.synchronized {

      if (buf.length == 0) {
        return 0
      }

      if (slide == 0) {
        return 0
      }

      // off will not larger than slide
      val realLen = math.min(slide - off, buf.length)

      if (realLen == 0) {
        return 0
      }

      val data = buf.slice(0, realLen)

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
          if (addNode._1 >= node._1 && addNode._2 <= node._2) {
            addNode = node
          } else if (addNode._1 <= node._1 && addNode._2 >= node._2) {
            // addNode = addNode
          } else if (addNode._1 <= node._1 && addNode._2 <= node._2) {
            addNode = (addNode._1, node._2, addNode._3.slice(0, node._1 - addNode._1) ++ node._3)
          } else {
            addNode = (node._1, addNode._2, node._3.slice(0, addNode._1 - node._1) ++ addNode._3)
          }
        }
      }
      templinkList = templinkList :+ addNode

      linkList = templinkList

      var templength = 0

      if (linkList(0)._1 == 0) {
        if (recvBuf.length == 0) {
          this.notify
        }
        recvBuf = recvBuf ++ linkList(0)._3
        templength = linkList(0)._3.length
        linkList = linkList.slice(1, linkList.length)
        slide -= templength

        // modify the length for begin and end of each entry
        var tempModify: Array[(Int, Int, Array[Byte])] = new Array[(Int, Int, Array[Byte])](0)
        for (node <- linkList) {
          tempModify = tempModify :+ (node._1 - templength, node._2 - templength, node._3)
        }
        linkList = tempModify
      }
      // printTemp(templength)
      templength
    }
  }

  // debug function to print
  def printTemp(submit: Int) {
    println("====================" + " update: " + submit)
    for (i <- linkList) {
      println("Start: " + i._1 + " End: " + i._2 + " Data: " + i._3.length)
    }
  }

  def waitAvailable() {
    this.synchronized {
      if (slide == capacity) {
        return
      }
      this.busy = true
    }
    this.semaphoreCheckAvailalbe.acquire
  }

  def getSliding(): Int = {
    this.synchronized {
      slide
    }
  }

  def wakeup() {
    this.synchronized {
      this.notify
    }
  }
}