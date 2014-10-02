package ip

import util.PrintIPPacket

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

  }
}