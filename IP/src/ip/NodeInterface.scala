package ip

import util._
import java.net.{ DatagramSocket, InetAddress, DatagramPacket, InetSocketAddress }
import java.io.IOException
import scala.collection.mutable.HashMap
import scala.actors.threadpool.locks.{ReentrantLock, ReentrantReadWriteLock}

class NodeInterface {
  val Rip = 200
  val Data = 0
  val DefaultHeadLength = 20
  // TODO
  val MaxTTL = 256
  var localPhysPort: Int = _
  var localPhysHost: InetAddress = _
  var socket: DatagramSocket = _
  var linkInterfaceArray: Array[LinkInterface] = _
  // dst addr, cost, next addr
  val routingTable = new HashMap[InetAddress, (Int, InetAddress)]

  val UsageCommand = "We only accept: [i]nterfaces, [r]outes," +
    "[d]own <integer>, [u]p <integer>, [s]end <vip> <proto> <string>, [q]uit"

  // remote phys addr + port => interface
  var physAddrToInterface = new HashMap[InetSocketAddress, LinkInterface]

  // remote virtual addr => interface
  var virtAddrToInterface = new HashMap[InetAddress, LinkInterface]

  
  // without locking UDP, send and receive can be at the same time
  // read/write lock for routingTable
  val routingTableLock = new ReentrantReadWriteLock
  
  def initSocketAndInterfaces(file: String) {
    val lnx = ParseLinks.parseLinks(file)
    localPhysPort = lnx.localPhysPort
    localPhysHost = lnx.localPhysHost

    // init socket
    socket = new DatagramSocket(lnx.localPhysPort, lnx.localPhysHost)
    linkInterfaceArray = new Array[LinkInterface](lnx.links.length)

    // init link interfaces
    var id = 0
    for (link <- lnx.links) {
      val interface = new LinkInterface(link, id)
      linkInterfaceArray(id) = interface

      physAddrToInterface.put(new InetSocketAddress(interface.link.remotePhysHost, interface.link.remotePhysPort), interface)
      virtAddrToInterface.put(interface.link.remoteVirtIP, interface)
      // routingTable.put(link.localVirtIP, (16, link.remoteVirtIP))
      id += 1
    }
  }

  def sendPacket(interface: LinkInterface) {
    if (interface.isUpOrDown) {
      if (!interface.outBuffer.isEmpty) {
        val pkt = interface.outBuffer.bufferRead
        val headBuf: Array[Byte] = ConvertObject.headToByte(pkt.head)

        // checksum remove
        headBuf(10) = 0
        headBuf(11) = 0
        val checkSum = IPSum.ipsum(headBuf)

        // fill checksum
        headBuf(10) = ((checkSum >> 8) & 0xff).asInstanceOf[Byte]
        headBuf(11) = (checkSum & 0xff).asInstanceOf[Byte]

        if (headBuf != null) {
          // TODO: static constant MTU
          val totalBuf = headBuf ++ pkt.payLoad
          val packet = new DatagramPacket(totalBuf, totalBuf.length, interface.link.remotePhysHost, interface.link.remotePhysPort)
          try {
            socket.send(packet)
          } catch {
            // disconnect
            case ex: IOException => println("send packet")
          }
        }
      }
    } else {
      println("interface " + interface.id + "down")
    }
  }

  def recvPacket() {
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

      val headTotalBuf = headByteBuf ++ headBuf

      // checksum valid
      val checkSum = IPSum ipsum headTotalBuf
      if (checkSum != 0) {
        println("This packet has wrong checksum!")
        return
      }

      // convert to IPHead
      pkt.head = ConvertObject.byteToHead(headTotalBuf)

      // payload
      val payLoadBuf = new Array[Byte](pkt.head.totlen - len)
      val packetPayLoad = new DatagramPacket(payLoadBuf, payLoadBuf.length)
      socket.receive(packetPayLoad)
      pkt.payLoad = payLoadBuf

      val remote = packetHead.getSocketAddress().asInstanceOf[InetSocketAddress]
      val option = physAddrToInterface.get(remote)
      option match {
        case Some(interface) => {
          if (interface.isUpOrDown) {
            interface.inBuffer.bufferWrite(pkt)
          } else {
            println("interface " + interface.id + "down")
          }
        }
        case None => println("Receiving packet from " + remote.getHostString() + ":" + remote.getPort())
      }

    } catch {
      // disconnect
      case ex: IOException => println("recv packet")
    }

  }

  def generateAndSendPacket(arr: Array[String], line: String) {
    // TODO: maybe user's input may contains 
    if (arr.length <= 3) {
      println(UsageCommand)
    } else {
      val dstVirtIp = arr(1)
      // Check whether vip is in the routing table
      // lock
      routingTableLock.readLock.lock
      val flag = routingTable.contains(InetAddress.getByName(dstVirtIp))
      routingTableLock.readLock.unlock
      if (!flag) {
        println("Destination Unreachable!")
      } else if (arr(2).forall(_.isDigit)) {
        // Check whether the protocol is test data
        val proto = arr(2).toInt
        if (proto == Data) {
          val userData = line.getBytes().slice((arr(0).length + arr(1).length + arr(2).length + 3), line.length)
          generateIPPacket(InetAddress.getByName(dstVirtIp), proto, userData)
        } else {
          println("Unsupport Protocol: " + proto)
        }
      } else {
        println(UsageCommand)
      }
    }
  }

  def generateIPPacket(virtIP: InetAddress, proto: Int, userData: Array[Byte]) {
    val pkt = new IPPacket
    pkt.payLoad = userData

    val head = new IPHead

    head.versionAndIhl = ((4 << 4) | DefaultHeadLength).asInstanceOf[Short]
    // TODO
    head.tos = 0
    head.totlen = DefaultHeadLength + userData.length
    head.fragoff = 0
    head.ttl = MaxTTL.asInstanceOf[Short]
    head.protocol = proto.asInstanceOf[Short]
    // send will update checksum
    head.check = 0

    // lock
    routingTableLock.readLock.lock
    val option = routingTable.get(virtIP)
    routingTableLock.readLock.unlock
    option match {
      case Some((cost, nextAddr)) => {
        val virtSrcIP = virtAddrToInterface.get(nextAddr)
        virtSrcIP match {
          case Some(interface) => {
            head.saddr = interface.link.localVirtIP

            head.daddr = virtIP

            pkt.head = head
            pkt.head.id = pkt.hashCode()

            if (interface.isUpOrDown) {
              interface.outBuffer.bufferWrite(pkt)
            } else {
              println("interface " + interface.id + "down: " + "no way to send out")
            }
          }
          case None => println("Fail to get source virtual IP address!")
        }
      }
      case None => println("Destination Unreachable!")
    }
  }

  def printInterfaces(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      println("Interfaces:")
      var i = 0;
      for (interface <- linkInterfaceArray) {
        interface.linkInterfacePrint
      }
    }
  }

  def printRoutes(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      println("Routing table:")
      // lock
      routingTableLock.readLock.lock
      for (entry <- routingTable) {
        var throughAddr: String = ""
        if (entry._1.getHostAddress() == entry._2._2.getHostAddress()) {
          throughAddr = "self"
        } else {
          throughAddr = entry._2._2.getHostAddress()
        }

        println("Route to " + entry._1.getHostAddress() + " with cost " + entry._2._1 +
          ", through " + throughAddr)
      }
      routingTableLock.readLock.unlock
    }
  }

  def interfacesDown(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).forall(_.isDigit)) {
      val num = arr(1).toInt

      if (num < linkInterfaceArray.length) {
        linkInterfaceArray(num).bringDown;
      } else {
        println("No such interface")
      }
    } else {
      println("input should be number")
    }
  }

  def interfacesUp(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).forall(_.isDigit)) {
      val num = arr(1).toInt

      if (num < linkInterfaceArray.length) {
        linkInterfaceArray(num).bringUp
      } else {
        println("No such interface")
      }
    } else {
      println("input should be number")
    }
  }
}