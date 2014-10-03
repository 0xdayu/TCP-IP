package ip

import util._
import scala.actors.threadpool.locks.ReentrantReadWriteLock
import java.net.InetAddress

/*
 * Interface as a linker layer
 */
class LinkInterface(_link: Link, _id: Int) {
  private var upOrDown: Boolean = true // up is true, down is false
  private val MaxBufferSize = 1024 * 1024 // 1MB
  val inBuffer = new FIFOBuffer(MaxBufferSize)
  val outBuffer = new FIFOBuffer(MaxBufferSize)
  val link = _link
  val id = _id

  def compareIP(ip: InetAddress) = ip == getLocalIP

  // this is virtual IP
  def getLocalIP = link.localVirtIP

  // this is virtual IP
  def getRemoteIP = link.remoteVirtIP

  def isUpOrDown = this.synchronized { upOrDown }

  def bringDown {
    this.synchronized {
      if (upOrDown) {
        upOrDown = false

        // clean inBuffer/outBuffer
        while (!inBuffer.isEmpty) {
          inBuffer.bufferRead
        }
        while (!outBuffer.isEmpty) {
          outBuffer.bufferRead
        }

        println("interface " + id + " down")
      } else {
        println("interface " + id + " already down")
      }
    }
  }

  def bringUp {
    this.synchronized {
      if (!upOrDown) {
        upOrDown = true;
        println("interface " + id + " up")
      } else {
        println("interface " + id + " already up")
      }
    }
  }

  def linkInterfacePrint {
    this.synchronized {
      val str = if (isUpOrDown) "UP" else "DOWN"
      println("\t" + id + ": " + getLocalIP.getHostAddress +
        " -> " + getRemoteIP.getHostAddress + ", " + str)
    }
  }
}