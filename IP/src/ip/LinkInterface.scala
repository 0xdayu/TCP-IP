package ip

import util._
import java.net.{DatagramSocket, DatagramPacket}
import java.io.{ IOException }
import scala.collection.mutable.ArrayBuffer

/*
 * Interface as a linker layer
 */
class LinkInterface(link: Link, id: Int, socket: DatagramSocket) {
  private var upOrDown: Boolean = true // up is true, down is false
  private val MaxBufferSize = 64 * 1024
  val inBuffer = new FIFOBuffer(MaxBufferSize)
  val outBuffer = new FIFOBuffer(MaxBufferSize)
  
  def sendPacket() {
    if (isUpOrDown) {
      val pkt = outBuffer.bufferRead
      val headBuf: Array[Byte] = ConvertObject.headToByte(pkt.head)
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

  def recvPacket() {
    if (isUpOrDown){
      try {
        val pkt = new IPPacket
        
        // head first byte
        val headByteBuf = new Array[Byte](1)
        val headByte = new DatagramPacket(headByteBuf, 1)
        socket.receive(headByte)
        val len = ConvertObject.headLen(headByteBuf(0))
        
        // head other bytes
        val headBuf = new Array[Byte](len - 1)
        val packetHead = new DatagramPacket(headBuf, headBuf.length)
        socket.receive(packetHead)
        
        // convert to IPHead
        pkt.head = ConvertObject.byteToHead(headByteBuf ++ headBuf)
        
        // payload
        val payLoadBuf = new Array[Byte](ConvertNumber.uint16ToInt(pkt.head.totlen) - len)
        val packetPayLoad = new DatagramPacket(payLoadBuf, payLoadBuf.length)
        socket.receive(packetPayLoad)
        pkt.payLoad = payLoadBuf
        
        inBuffer.bufferWrite(pkt)
      } catch {
        // disconnect
        case ex: IOException => println("recv packet")
      }
    } else {
      println("interface " + id + "down")
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