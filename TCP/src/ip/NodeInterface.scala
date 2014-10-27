package ip

import iputil._
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
  val MinMTU = 68
  val MaxPacket = 64 * 1024
  val MaxTTL = 15
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

  // remote phys addr + port => interface
  var physAddrToInterface = new HashMap[InetSocketAddress, LinkInterface]

  // remote virtual addr => interface
  var virtAddrToInterface = new HashMap[InetAddress, LinkInterface]

  // HashMap for Fragmentation
  // [id, (time, currentSize, totalSize, waitingArray)]
  val TimeFrag = 20000
  val fragPacket = new LinkedHashMap[Int, (Long, Int, Int, Array[Byte])]
  val fragTimeOut = new Timer
  val fragPacketLock = new ReentrantReadWriteLock

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

    // timeout of each 20 seconds
    fragTimeOut.schedule(new FragTimeOut(this), TimeFrag)
  }

  def sendPacket(interface: LinkInterface) {
    if (interface.isUpOrDown) {
      if (!interface.outBuffer.isEmpty) {
        val pkt = interface.outBuffer.bufferRead
        val packetFragmentationArray = IPPacketFragmentation.fragment(pkt, interface.mtu)

        if (packetFragmentationArray != null) {
          for (newPkt <- packetFragmentationArray) {
            val headBuf: Array[Byte] = ConvertObject.headToByte(newPkt.head)

            // checksum remove
            headBuf(10) = 0
            headBuf(11) = 0

            val checkSum = IPSum.ipsum(headBuf)

            newPkt.head.check = (checkSum & 0xffff).asInstanceOf[Int]

            // fill checksum
            headBuf(10) = ((checkSum >> 8) & 0xff).asInstanceOf[Byte]
            headBuf(11) = (checkSum & 0xff).asInstanceOf[Byte]

            // Test
            if (newPkt.head.protocol == Data) {
              PrintIPPacket.printIPPacket(newPkt, false, false, false)
              // PrintIPPacket.printIPPacket(newPkt, true, true, false)
            } else {
              PrintIPPacket.printIPPacket(newPkt, false, false, true)
              // PrintIPPacket.printIPPacket(newPkt, true, true, true)
            }

            if (headBuf != null) {
              val totalBuf = headBuf ++ newPkt.payLoad

              val packet = new DatagramPacket(totalBuf, totalBuf.length, interface.link.remotePhysHost, interface.link.remotePhysPort)

              try {
                socket.send(packet)
              } catch {
                // disconnect
                case ex: IOException => println("Error: send packet, cannot reach that remotePhysHost")
              }
            }
          }
        }
      }
    } else {
      //println("Send: interface " + interface.id + " down, drop the packet")
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

      // drop 
      if (pkt.head == null) {
        return
      }

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
            // Whether the packet needs to be reassembled
            // check all the interfaces
            var flag = false
            for (_interface <- linkInterfaceArray) {
              if (pkt.head.daddr == _interface.getLocalIP) {
                flag = true
              }
            }

            if (flag && pkt.head.fragoff != 0 && (pkt.head.fragoff >> 14) != 1) {
              val reassembledPacket = IPPacketFragmentation.reassemblePacket(fragPacket, pkt, fragPacketLock)
              if (reassembledPacket != null) {
                interface.inBuffer.bufferWrite(reassembledPacket)
              }
            } else {
              interface.inBuffer.bufferWrite(pkt)
            }
          } else {
            // println("Receive: interface " + interface.id + " down, drop the packet")
          }
        }
        case None => println("Receiving packet from " + remote.getHostString() + ":" + remote.getPort())
      }

    } catch {
      // disconnect
      case ex: IOException => println("Close the socket")
    }
  }

  def generateAndSendPacket(dstVirtIp: String, proto: Int, data: Array[Byte]) {
    // Check whether rip is in the routing table
    // lock
    routingTableLock.readLock.lock
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
            if (proto == Data) {
              println("Local printing: " + new String(data.map(_.toChar)))
            } else {
              println("Unsupport Protocol: " + proto)
            }
          } else {
            // println("interface " + interface.id + "down: " + "no way to send out")
          }

          return
        }
      }
    }

    routingTableLock.readLock.unlock
    if (!flag) {
      println("Destination Unreachable!")
    } else {
      // Check whether the protocol is test data
      if (proto == Data) {
        if (data.length > DefaultMTU - DefaultHeadLength) {
          println("Maximum Transfer Unit is " + DefaultMTU + ", but the packet size is " + data.length + DefaultHeadLength)
        } else {
          generateIPPacket(InetAddress.getByName(dstVirtIp), proto, data, true)
        }
      } else {
        println("Unsupport Protocol: " + proto)
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
                // println("interface " + interface.id + "down: " + "no way to send out")
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
            // println("interface " + interface.id + "down: " + "no way to send out")
          }
        }
        case None => println("Fail to get source virtual IP address!")
      }
    }
  }

  def printInterfaces() {
    println("Interfaces:")
    var i = 0;
    for (interface <- linkInterfaceArray) {
      interface.linkInterfacePrint
    }
  }

  def printRoutes() {
    println("Routing table: ")
    // lock
    routingTableLock.readLock.lock
    if (routingTable.size == 0) {
      println("[no routes]")
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

  def interfacesDown(num: Int) {
    if (num < linkInterfaceArray.length && num >= 0) {
      linkInterfaceArray(num).bringDown
    } else {
      println("No such interface: " + num)
    }
  }

  def interfacesUp(num: Int) {
    if (num < linkInterfaceArray.length && num >= 0) {
      linkInterfaceArray(num).bringUp
    } else {
      println("No such interface: " + num)
    }
  }

  def setMTU(num: Int, mtu: Int) {
    if (num < linkInterfaceArray.length && num >= 0) {
      // at least 68
      if (mtu >= MinMTU) {
        linkInterfaceArray(num).mtu = mtu
      } else {
        println("Wrong MTU size. The size should be at least: " + MinMTU)
      }
    } else {
      println("No such interface: " + num)
    }
  }
}