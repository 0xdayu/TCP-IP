package ip

import util._
import scala.actors.threadpool.locks.ReentrantReadWriteLock

/*
 * Interface as a linker layer
 */
class LinkInterface(_link: Link, _id: Int) {
  private var upOrDown: Boolean = true // up is true, down is false
  private val MaxBufferSize = 64 * 1024
  val inBuffer = new FIFOBuffer(MaxBufferSize)
  val outBuffer = new FIFOBuffer(MaxBufferSize)
  val link = _link
  val id = _id

  def compareIP(ip: String) = ip == getLocalIP

  // this is virtual IP as string
  def getLocalIP = link.localVirtIP.getHostAddress()

  // this is virtual IP as string
  def getRemoteIP = link.remoteVirtIP.getHostAddress()

  def isUpOrDown = this.synchronized { upOrDown }

  def bringDown {
    this.synchronized {
      if (!isUpOrDown) {
        upOrDown = false
        println("interface " + id + "down")
      } else {
        println("interface " + id + "already down")
      }
    }
  }

  def bringUp {
    this.synchronized {
      if (isUpOrDown) {
        upOrDown = true;
        println("interface " + id + "up")
      } else {
        println("interface " + id + "already up")
      }
    }
  }

  def linkInterfacePrint {
    this.synchronized {
      val str = if (isUpOrDown) "UP" else "DOWN"
      println("\t" + id + ": " + getLocalIP +
        "->" + getRemoteIP + ", " + str)
    }
  }
}