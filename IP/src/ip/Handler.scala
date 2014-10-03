package ip

import util.{ PrintIPPacket, ConvertObject }
import java.net.InetAddress
import scala.collection.mutable.ArrayBuffer

object Handler {
  def forwardHandler(packet: IPPacket, nodeInterface: NodeInterface) {
    val dstIpAddr = packet.head.daddr

    // local interfaces check
    for (interface <- nodeInterface.linkInterfaceArray) {
      if (interface.compareIP(dstIpAddr)) {
        PrintIPPacket.printIPPacket(packet, false, false, false)
        return
      }
    }

    // forwarding
    // lock
    nodeInterface.routingTableLock.readLock.lock
    val option = nodeInterface.routingTable.get(dstIpAddr)
    nodeInterface.routingTableLock.readLock.unlock
    option match {
      case Some((cost, nextAddr)) => {
        val interface = nodeInterface.virtAddrToInterface.get(nextAddr)
        interface match {
          case Some(_interface) => {
            if (_interface.isUpOrDown) {
              // Decrease TTL by one
              packet.head.ttl = (packet.head.ttl - 1).asInstanceOf[Short]
              // drop if ttl == 0
              if (packet.head.ttl != 0) {
                _interface.outBuffer.bufferWrite(packet)
              } else {
                println("ttl == 0, we need to drop this packet")
              }
            }
          }
          case None => {
            println("Fail to find outport interface or the interface is in close state.")
          }
        }
      }
      case None => {
        println("There's no match rule in the routing table.")
      }
    }
  }

  def ripHandler(packet: IPPacket, nodeInterface: NodeInterface) {
    val interface = nodeInterface.virtAddrToInterface.get(packet.head.saddr)
    interface match {
      case Some(_) => // nothing here
      case None =>
        println("Error destination address of RIP: " + packet.head.daddr.getAddress)
        return
    }

    val rip = ConvertObject.byteToRIP(packet.payLoad)
    if (rip.command == nodeInterface.RIPRequest) {
      // send back total routing table
      // lock
      nodeInterface.routingTableLock.readLock.lock
      val responseRIP = new RIP
      responseRIP.command = nodeInterface.RIPResponse
      responseRIP.numEntries = nodeInterface.routingTable.size
      var i = 0
      for (entry <- nodeInterface.routingTable) {
        responseRIP.entries(i) = (entry._2._1, entry._1)
        i += 1
      }
      nodeInterface.ripResponse(packet.head.saddr, responseRIP)
      nodeInterface.routingTableLock.readLock.unlock
    } else {
      val updateRIP = new RIP
      updateRIP.command = nodeInterface.RIPResponse
      updateRIP.numEntries = 0
      var array = new ArrayBuffer[(Int, InetAddress)]

      val remote = packet.head.saddr

      nodeInterface.routingTableLock.writeLock.lock
      for (entry <- rip.entries) {
        val newCost = entry._1 + 1
        val pair = nodeInterface.routingTable.get(entry._2)
        pair match {
          case Some((cost, nextHop)) => {
            if (cost > newCost) {
              // update
              nodeInterface.routingTable.put(entry._2, (newCost, remote))
              // TODO: avoid loop
              updateRIP.numEntries += 1
              array += (newCost, entry._2).asInstanceOf[(Int, InetAddress)]
            } // else nothing
          }
          case None => {
            // same
            nodeInterface.routingTable.put(entry._2, (newCost, remote))
            updateRIP.numEntries += 1
            array += (newCost, entry._2).asInstanceOf[(Int, InetAddress)]
          }
        }
      }
      nodeInterface.routingTableLock.writeLock.unlock

      updateRIP.entries = array.toArray

      for (interface <- nodeInterface.linkInterfaceArray) {
        if (interface.getRemoteIP != remote) {
          nodeInterface.ripResponse(interface.getRemoteIP, updateRIP)
        }
      }

      var inifinityArray = new ArrayBuffer[(Int, InetAddress)]
      for (entry <- updateRIP.entries) {
        inifinityArray += (nodeInterface.RIPInifinity, entry._2).asInstanceOf[(Int, InetAddress)]
      }

      updateRIP.entries = inifinityArray.toArray
      nodeInterface.ripResponse(remote, updateRIP)
    }
  }
}