package ip

import util._
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.io.{ IOException }
import scala.collection.mutable.ArrayBuffer

/*
 * Interface as a linker layer
 */
class LinkInterface(link: Link, id: Int) {
  private var socket: DatagramSocket = new DatagramSocket(link.localPhysPort, link.localPhysHost)
  private var upOrDown: Boolean = true // up is true, down is false
  private val HeadLen = 20
  
  def sendPacket(pkt: IPPacket) {
    if (isUpOrDown) {
      val headBuf: Array[Byte] = ConvertObject objectToByte[IPHead] pkt.head
      if (headBuf != null) {
        // TODO: static constant MTU
        val totalBuf = headBuf ++ pkt.payLoad 
        val packet = new DatagramPacket(totalBuf, totalBuf.length, link.remotePhysHost, link.remotePhysPort)
        try {
          socket.send(packet)
        } catch {
          // disconnect
          case ex: IOException => println("send packet")
        }
      }
    } else {
      println("interface " + id + "down")
    }
  }

  def recvPacket(buf: Array[Byte]): IPPacket = {
    if (!isUpOrDown) {
      null
    } else {
      try {
        val pkt = new IPPacket
        
        // head
        val headBuf = new Array[Byte](HeadLen)
        val packetHead = new DatagramPacket(headBuf, headBuf.length)
        socket.receive(packetHead)
        pkt.head = ConvertObject byteToObject[IPHead] headBuf
        
        // payload
        val payLoadBuf = new Array[Byte](pkt.head.totlen - HeadLen)
        val packetPayLoad = new DatagramPacket(payLoadBuf, payLoadBuf.length)
        socket.receive(packetPayLoad)
        pkt.payLoad = payLoadBuf
        
        pkt
      } catch {
        // disconnect
        case ex: IOException => println("recv packet"); null
      }
    }
  }

  def compareIP(ip: String) = ip == getLocalIP

  // this is virtual IP as string
  def getLocalIP = link.localVirtIP.getHostAddress()

  // this is virtual IP as string
  def getRemoteIP = link.remoteVirtIP.getHostAddress()

  def isUpOrDown = upOrDown

  def bringDown {
    if (!isUpOrDown) {
      upOrDown = false
      println("interface " + id + "down")
    } else {
      println("interface " + id + "already down")
    }
  }

  def bringUp {
    if (isUpOrDown) {
      upOrDown = true;
      println("interface " + id + "up")
    } else {
      println("interface " + id + "already up")
    }
  }

  def linkInterfacePrint {
    val str = if (isUpOrDown) "UP" else "DOWN"
    println("\t" + id + ": " + getLocalIP +
      "->" + getRemoteIP + ", " + str)
  }

}