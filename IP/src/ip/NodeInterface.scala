package ip

import util._
import java.net.{ DatagramSocket, InetAddress, DatagramPacket, InetSocketAddress }
import java.io.IOException
import scala.collection.mutable.{ HashMap, LinkedHashMap }
import scala.actors.threadpool.locks.{ ReentrantLock, ReentrantReadWriteLock }
import java.util.Timer

class NodeInterface {
  val Rip = 200
  val Data = 0
  val DefaultVersion = 4
  val DefaultHeadLength = 20
  val DefaultMTU = 1400
  val MaxPacket = 64 * 1024
  val MaxTTL = 16
  val RIPInifinity = 16
  val RIPRequest = 1
  val RIPResponse = 2

  var idCount = 0

  var localPhysPort: Int = _
  var localPhysHost: InetAddress = _
  var socket: DatagramSocket = _
  var linkInterfaceArray: Array[LinkInterface] = _
  // dst addr, cost, next addr
  val routingTable = new HashMap[InetAddress, (Int, InetAddress)]

  // 5 seconds
  val TimePeriodic = 5000
  val periodicUpdate = new Timer

  // 12 seconds
  val TimeExpire = 12000
  val entryExpire = new LinkedHashMap[InetAddress, Long]
  val expire = new Timer
  val entryExpireLock = new ReentrantReadWriteLock

  val UsageCommand = "We only accept: [h]elp, [i]nterfaces, [r]outes," +
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
    linkInterfaceArray = Array.ofDim[LinkInterface](lnx.links.length)

    // init link interfaces
    var id = 0
    for (link <- lnx.links) {
      val interface = new LinkInterface(link, id, this)
      linkInterfaceArray(id) = interface

      physAddrToInterface.put(new InetSocketAddress(interface.link.remotePhysHost, interface.link.remotePhysPort), interface)
      virtAddrToInterface.put(interface.link.remoteVirtIP, interface)

      // When only this node is up, the routing table should be empty.

      // Fire up all interfaces: including RIP request
      interface.bringUp

      id += 1
    }

    // timeout of each 5 seconds, we start after 5 seconds (first time)
    periodicUpdate.schedule(new PeriodicUpdate(this), TimePeriodic, TimePeriodic)

    // timeout of each 12 seconds, we start after 5 seconds (first time)
    expire.schedule(new Expire(this), TimeExpire)
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

        pkt.head.check = (checkSum & 0xffff).asInstanceOf[Int]

        // fill checksum
        headBuf(10) = ((checkSum >> 8) & 0xff).asInstanceOf[Byte]
        headBuf(11) = (checkSum & 0xff).asInstanceOf[Byte]

        // XXXXXXXX Test
        if (pkt.head.protocol == Data) {
          PrintIPPacket.printIPPacket(pkt, false, false, false)
          // PrintIPPacket.printIPPacket(pkt, true, true, false)
        } else {
          PrintIPPacket.printIPPacket(pkt, false, false, true)
          // PrintIPPacket.printIPPacket(pkt, true, true, true)
        }

        if (headBuf != null) {
          // TODO: static constant MTU
          val totalBuf = headBuf ++ pkt.payLoad

          val packet = new DatagramPacket(totalBuf, totalBuf.length, interface.link.remotePhysHost, interface.link.remotePhysPort)

          try {
            socket.send(packet)
          } catch {
            // disconnect
            case ex: IOException => println("Error: send packet, cannot reach that remotePhysHost")
          }
        }
      }
    } else {
      println("Send: interface " + interface.id + " down, drop the packet")
    }
  }

  def recvPacket() {
    try {
      val pkt = new IPPacket

      val maxBuf = Array.ofDim[Byte](MaxPacket)
      val packet = new DatagramPacket(maxBuf, MaxPacket)
      socket.receive(packet)

      // head first byte
      val len = ConvertObject.headLen(maxBuf(0))

      // head other bytes
      val headTotalBuf = maxBuf.slice(0, len)

      // checksum valid
      val checkSum = IPSum ipsum headTotalBuf
      if ((checkSum & 0xfff) != 0) {
        println("This packet has wrong checksum!")
        return
      }

      // convert to IPHead
      pkt.head = ConvertObject.byteToHead(headTotalBuf)

      if (((pkt.head.versionAndIhl >> 4) & 0xf).asInstanceOf[Byte] != 4) {
        println("We can only receive packet of IPv4")
        return
      }

      // payload
      pkt.payLoad = maxBuf.slice(len, pkt.head.totlen)

      val remote = packet.getSocketAddress().asInstanceOf[InetSocketAddress]
      val option = physAddrToInterface.get(remote)
      option match {
        case Some(interface) => {
          if (interface.isUpOrDown) {
            interface.inBuffer.bufferWrite(pkt)
          } else {
            println("Receive: interface " + interface.id + " down, drop the packet")
          }
        }
        case None => println("Receiving packet from " + remote.getHostString() + ":" + remote.getPort())
      }

    } catch {
      // disconnect
      case ex: IOException => println("Close the socket")
    }

  }

  def generateAndSendPacket(arr: Array[String], line: String) {
    if (arr.length <= 3) {
      println(UsageCommand)
    } else {
      val dstVirtIp = arr(1)
      // Check whether rip is in the routing table
      // lock
      routingTableLock.readLock.lock
      // TODO: static constant MTU
      var flag = false
      try {
        flag = routingTable.contains(InetAddress.getByName(dstVirtIp))
      } catch {
        case _: Throwable =>
          println("Invalid IP address")
          return
      }

      if (!flag) {
        for (interface <- linkInterfaceArray) {
          if (interface.getLocalIP == InetAddress.getByName(dstVirtIp)) {
            // local print
            if (interface.isUpOrDown) {
              if (arr(2).forall(_.isDigit)) {
                val proto = arr(2).toInt
                if (proto == Data) {
                  val len = line.indexOf(arr(2), line.indexOf(arr(1)) + arr(1).length) + 1 + arr(2).length
                  println("Local printing: " + line.slice(len, line.length))
                } else {
                  println("Unsupport Protocol: " + proto)
                }
              } else {
                println(UsageCommand)
              }
            } else {
              println("interface " + interface.id + "down: " + "no way to send out")
            }

            return
          }
        }
      }

      routingTableLock.readLock.unlock
      if (!flag) {
        println("Destination Unreachable!")
      } else if (arr(2).forall(_.isDigit)) {
        // Check whether the protocol is test data
        val proto = arr(2).toInt
        if (proto == Data) {

          val len = line.indexOf(arr(2), line.indexOf(arr(1)) + arr(1).length) + 1 + arr(2).length
          val userData = line.getBytes().slice(len, line.length)

          if (userData.length > DefaultMTU - DefaultHeadLength) {
            println("Maximum Transfer Unit is " + DefaultMTU + ", but the packet size is " + userData.length + DefaultHeadLength)
          } else {
            generateIPPacket(InetAddress.getByName(dstVirtIp), proto, userData, true)
          }
        } else {
          println("Unsupport Protocol: " + proto)
        }
      } else {
        println(UsageCommand)
      }
    }
  }

  def ripRequest(virtIP: InetAddress) {
    val rip = new RIP
    rip.command = RIPRequest
    rip.numEntries = 0
    rip.entries = Array.empty
    val userData = ConvertObject.RIPToByte(rip)
    generateIPPacket(virtIP, Rip, userData, false)
  }

  def ripResponse(virtIP: InetAddress, rip: RIP) {
    val userData = ConvertObject.RIPToByte(rip)
    generateIPPacket(virtIP, Rip, userData, false)
  }

  def generateIPPacket(virtIP: InetAddress, proto: Int, userData: Array[Byte], checkTable: Boolean) {
    val pkt = new IPPacket
    pkt.payLoad = userData

    val head = new IPHead

    head.versionAndIhl = ((DefaultVersion << 4) | (DefaultHeadLength / 4)).asInstanceOf[Short]
    head.tos = 0
    head.totlen = DefaultHeadLength + userData.length
    // only need final 16 bits: 0 ~ 65535
    // for fragmentation
    head.id = idCount

    if (idCount == 65535) {
      idCount = 0
    } else {
      idCount += 1
    }

    head.fragoff = 0
    head.ttl = MaxTTL.asInstanceOf[Short]
    head.protocol = proto.asInstanceOf[Short]
    // send will update checksum
    head.check = 0

    if (checkTable) {
      // lock
      routingTableLock.readLock.lock
      val option = routingTable.get(virtIP)
      routingTableLock.readLock.unlock
      option match {
        case Some((cost, nextHop)) => {
          val virtSrcIP = virtAddrToInterface.get(nextHop)
          virtSrcIP match {
            case Some(interface) => {
              head.saddr = interface.link.localVirtIP

              head.daddr = virtIP

              pkt.head = head

              if (interface.isUpOrDown) {
                if (cost != RIPInifinity) {
                  interface.outBuffer.bufferWrite(pkt)
                } else {
                  println("The packet cannot go to inifinity address!")
                }
              } else {
                println("interface " + interface.id + "down: " + "no way to send out")
              }
            }
            case None => println("Fail to get next hop IP address: " + nextHop.getHostAddress)
          }
        }
        case None => println("Destination Unreachable!")
      }
    } else {
      val virtSrcIP = virtAddrToInterface.get(virtIP)
      virtSrcIP match {
        case Some(interface) => {
          head.saddr = interface.link.localVirtIP

          head.daddr = virtIP

          pkt.head = head

          if (interface.isUpOrDown) {
            interface.outBuffer.bufferWrite(pkt)
          } else {
            println("interface " + interface.id + "down: " + "no way to send out")
          }
        }
        case None => println("Fail to get source virtual IP address!")
      }
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
      println("Routing table: ")
      // lock
      routingTableLock.readLock.lock
      if (routingTable.size == 0) {
        println("Empty")
      } else {
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
      }
      routingTableLock.readLock.unlock
    }
  }

  def interfacesDown(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      val num = arr(1).trim.toInt

      if (num < linkInterfaceArray.length) {
        linkInterfaceArray(num).bringDown
      } else {
        println("No such interface: " + num)
      }
    } else {
      println("input should be number: " + arr(1).trim)
    }
  }

  def interfacesUp(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      val num = arr(1).trim.toInt

      if (num < linkInterfaceArray.length) {
        linkInterfaceArray(num).bringUp
      } else {
        println("No such interface: " + num)
      }
    } else {
      println("input should be number: " + arr(1).trim)
    }
  }
}