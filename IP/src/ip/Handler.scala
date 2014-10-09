package ip

import util.{ PrintIPPacket, ConvertObject }
import java.net.InetAddress
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

object Handler {
  def forwardHandler(packet: IPPacket, nodeInterface: NodeInterface) {
    val dstIpAddr = packet.head.daddr

    // println("TTL: "+packet.head.ttl)
    // local interfaces check
    for (interface <- nodeInterface.linkInterfaceArray) {
      if (interface.compareIP(dstIpAddr)) {
        if (packet.payLoad.isEmpty) {
          println("Nothing in payload")
        } else {
          println(new String(packet.payLoad.map(_.toChar)))
        }
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
              if (cost != nodeInterface.RIPInifinity) {
                // Decrease TTL by one
                packet.head.ttl = (packet.head.ttl - 1).asInstanceOf[Short]
                // drop if ttl == 0
                if (packet.head.ttl != 0) {
                  _interface.outBuffer.bufferWrite(packet)
                } else {
                  println("ttl == 0, we need to drop this packet")
                }
              } else {
                println("The packet cannot go to inifinity address!")
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
    // deal bad rip
    if (rip == null) {
      return
    }

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
      } else {
        // if contains, we should check whether the next hop is 1
        // no possible to get null
        val (cost, nextHop) = nodeInterface.routingTable.get(packet.head.saddr).getOrElse(null)
        if (nextHop != packet.head.saddr) {
          nodeInterface.routingTable.put(packet.head.saddr, (1, packet.head.saddr))
          routingTableIsUpdated = true
        }
      }
      nodeInterface.routingTableLock.writeLock.unlock

      // update the time for that entry
      nodeInterface.entryExpireLock.writeLock.lock
      if (nodeInterface.entryExpire.contains(packet.head.saddr)) {
        // remove the old entry time
        nodeInterface.entryExpire.remove(packet.head.saddr)
      }
      nodeInterface.entryExpire.put(packet.head.saddr, System.currentTimeMillis + nodeInterface.TimeExpire)
      nodeInterface.entryExpireLock.writeLock.unlock

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
      val tempEntryArray = new ArrayBuffer[(Int, InetAddress)]

      for (entry <- nodeInterface.routingTable) {
        if (entry._2._2 == packet.head.saddr) {
          tempEntryArray += (nodeInterface.RIPInifinity, entry._1).asInstanceOf[(Int, InetAddress)] // cost, destination, poison reverse
        } else {
          tempEntryArray += (entry._2._1, entry._1).asInstanceOf[(Int, InetAddress)] // cost, destination
        }
      }

      // Announce all up link interfaces
      for (interface <- nodeInterface.linkInterfaceArray) {
        if (interface.isUpOrDown) {
          tempEntryArray += (0, interface.getLocalIP).asInstanceOf[(Int, InetAddress)] // cost, destination	
        }
      }
      responseRIP.numEntries = tempEntryArray.length
      responseRIP.entries = tempEntryArray.toArray

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
      } else {
        // if contains, we should check whether the next hop is 1
        // no possible to get null
        val (cost, nextHop) = nodeInterface.routingTable.get(packet.head.saddr).getOrElse(null)
        if (nextHop != packet.head.saddr) {
          nodeInterface.routingTable.put(packet.head.saddr, (1, packet.head.saddr))
          array += (1, packet.head.saddr).asInstanceOf[(Int, InetAddress)]
        }
      }

      // update the time for that entry
      nodeInterface.entryExpireLock.writeLock.lock
      if (nodeInterface.entryExpire.contains(packet.head.saddr)) {
        // remove the old entry time
        nodeInterface.entryExpire.remove(packet.head.saddr)
      }
      nodeInterface.entryExpire.put(packet.head.saddr, System.currentTimeMillis + nodeInterface.TimeExpire)
      nodeInterface.entryExpireLock.writeLock.unlock

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

          var isUpdated = false

          val pair = nodeInterface.routingTable.get(entry._2)
          pair match {
            case Some((cost, nextHop)) => {
              if (nextHop == packet.head.saddr) {
                if (newCost == nodeInterface.RIPInifinity) {
                  // delete that entry "now"
                  // 1. delete that expire time
                  // 2. delete from routing table
                  // 3. tell all the interfaces
                  nodeInterface.entryExpireLock.writeLock.lock
                  if (nodeInterface.entryExpire.contains(entry._2)) {
                    // remove the old entry time
                    nodeInterface.entryExpire.remove(entry._2)
                  }
                  nodeInterface.entryExpireLock.writeLock.unlock

                  nodeInterface.routingTable.remove(entry._2)

                  // tell all the interfaces with inifinity entry
                  val deleteRIP = new RIP
                  deleteRIP.command = nodeInterface.RIPResponse
                  deleteRIP.numEntries = 1
                  deleteRIP.entries = new Array[(Int, InetAddress)](1)
                  deleteRIP.entries(0) = (nodeInterface.RIPInifinity, entry._2)

                  for (interface <- nodeInterface.linkInterfaceArray) {
                    nodeInterface.ripResponse(interface.getRemoteIP, deleteRIP)
                  }

                } else if (newCost != cost) {
                  // the same next hop and we need to update no matter whether it is larger or smaller
                  nodeInterface.routingTable.put(entry._2, (newCost, nextHop))
                  array += (newCost, entry._2).asInstanceOf[(Int, InetAddress)]
                  isUpdated = true
                } else if (newCost == cost) {
                  // same the cost and same nextHop, we only update the time
                  isUpdated = true
                }
              } else if (cost > newCost) {
                // update (override the original cost)
                nodeInterface.routingTable.put(entry._2, (newCost, packet.head.saddr))
                array += (newCost, entry._2).asInstanceOf[(Int, InetAddress)]
                isUpdated = true
              } // else is no update
              // TODO: cost == newCost, does it need to update?
            }
            case None => {
              // same
              if (newCost != nodeInterface.RIPInifinity) {
                nodeInterface.routingTable.put(entry._2, (newCost, packet.head.saddr))
                array += (newCost, entry._2).asInstanceOf[(Int, InetAddress)]
                isUpdated = true
              }
            }
          }

          // update the time for that entry
          if (isUpdated) {
            nodeInterface.entryExpireLock.writeLock.lock
            if (nodeInterface.entryExpire.contains(entry._2)) {
              // remove the old entry time
              nodeInterface.entryExpire.remove(entry._2)
            }
            nodeInterface.entryExpire.put(entry._2, System.currentTimeMillis + nodeInterface.TimeExpire)
            nodeInterface.entryExpireLock.writeLock.unlock
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