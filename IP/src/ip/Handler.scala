package ip

import util.{ PrintIPPacket, ConvertObject }
import java.net.InetAddress
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

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
    // TODO: 
    // All response have added poison reverse
    // 1.Response request: response all routing table
    // 2.Period updates (5 sec): response all routing table
    // 3.Triggered updates: response all routing table

    val interface = nodeInterface.virtAddrToInterface.get(packet.head.saddr)
    interface match {
      case Some(_) => // nothing here
      case None =>
        println("Error destination address of RIP: " + packet.head.daddr.getAddress)
        return
    }

    val rip = ConvertObject.byteToRIP(packet.payLoad)
    if (rip.command == nodeInterface.RIPRequest) {
      // Receive request from the neighbor, means the neighbor is up, check the routing table to see whether it exists
      // If no, add to the routing table, otherwise ignore 
      // send back total routing table
      // lock

      var routingTableIsUpdated = false
      nodeInterface.routingTableLock.writeLock.lock
      if (!nodeInterface.routingTable.contains(packet.head.saddr)) {
        nodeInterface.routingTable.put(packet.head.saddr, (1, packet.head.saddr))
        routingTableIsUpdated = true
      }
      nodeInterface.routingTableLock.writeLock.unlock

      nodeInterface.routingTableLock.readLock.lock
      // modify and tell other interfaces
      if (routingTableIsUpdated) {
        val updateRIP = new RIP
        updateRIP.command = nodeInterface.RIPResponse
        updateRIP.numEntries = 1
        updateRIP.entries = new Array[(Int, InetAddress)](1)
        updateRIP.entries(0) = (1, packet.head.saddr)

        for (interface <- nodeInterface.linkInterfaceArray) {
          if (interface.getRemoteIP != packet.head.saddr) {
            nodeInterface.ripResponse(interface.getRemoteIP, updateRIP)
          }
        }
      }

      // response all the tables to request address
      val responseRIP = new RIP
      responseRIP.command = nodeInterface.RIPResponse
      responseRIP.numEntries = nodeInterface.routingTable.size
      responseRIP.entries = new Array[(Int, InetAddress)](nodeInterface.routingTable.size)
      var i = 0
      for (entry <- nodeInterface.routingTable) {
        if (entry._2._2 == packet.head.saddr) {
          responseRIP.entries(i) = (nodeInterface.RIPInifinity, entry._1) // cost, destination, poison reverse
        } else {
          responseRIP.entries(i) = (entry._2._1, entry._1) // cost, destination
        }
        i += 1
      }

      nodeInterface.ripResponse(packet.head.saddr, responseRIP)
      nodeInterface.routingTableLock.readLock.unlock
    } else { // Response all RIP request
      // First, insert neighbor to the routing table
      val updateRIP = new RIP
      updateRIP.command = nodeInterface.RIPResponse
      updateRIP.numEntries = 0
      var array = new ArrayBuffer[(Int, InetAddress)]

      nodeInterface.routingTableLock.writeLock.lock
      if (!nodeInterface.routingTable.contains(packet.head.saddr)) {
        nodeInterface.routingTable.put(packet.head.saddr, (1, packet.head.saddr))
        array += (1, packet.head.saddr).asInstanceOf[(Int, InetAddress)]
      }

      // deal with the total entries
      for (entry <- rip.entries) {
        breakable {
          // ignore the destination address is one interface of this router
          for (interface <- nodeInterface.linkInterfaceArray) {
            if (interface.getLocalIP == entry._2) {
              break
            }
          }
          // the max value is RIP inifinity
          var newCost = math.min(entry._1 + 1, nodeInterface.RIPInifinity)

          val pair = nodeInterface.routingTable.get(entry._2)
          pair match {
            case Some((cost, nextHop)) => {
              if (nextHop == packet.head.saddr) {
                // the same next hop and we need to update no matter whether it is larger or smaller
                if (newCost != cost) {
                  nodeInterface.routingTable.put(entry._2, (newCost, nextHop))
                  array += (newCost, entry._2).asInstanceOf[(Int, InetAddress)]
                }
              } else if (cost > newCost) {
                // update
                nodeInterface.routingTable.put(entry._2, (newCost, packet.head.saddr))
                array += (newCost, entry._2).asInstanceOf[(Int, InetAddress)]
              } // else nothing
            }
            case None => {
              // same
              nodeInterface.routingTable.put(entry._2, (newCost, packet.head.saddr))
              array += (newCost, entry._2).asInstanceOf[(Int, InetAddress)]
            }
          }
        }
      }
      nodeInterface.routingTableLock.writeLock.unlock

      if (array.length != 0) {
        // now, after updating the routing table, we can start to send
        updateRIP.numEntries = array.length
        updateRIP.entries = array.toArray

        for (interface <- nodeInterface.linkInterfaceArray) {
          if (interface.getRemoteIP != packet.head.saddr) {
            nodeInterface.ripResponse(interface.getRemoteIP, updateRIP)
          }
        }

        var inifinityArray = new ArrayBuffer[(Int, InetAddress)]
        for (entry <- array) {
          inifinityArray += (nodeInterface.RIPInifinity, entry._2).asInstanceOf[(Int, InetAddress)]
        }

        updateRIP.entries = inifinityArray.toArray
        nodeInterface.ripResponse(packet.head.saddr, updateRIP)
      }
    }
  }
}