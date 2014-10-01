package ip

object Handler {
	def forwardHandler(packet: IPPacket, nodeInterface: NodeInterface) {
	  //TODO: MUTEX LOCK
	  //TTL MODIFICATION
	  val dstIpAddr = packet.head.daddr
	  // lock
	  nodeInterface.routingTableLock.readLock.lock
	  val option = nodeInterface.routingTable.get(dstIpAddr)
	  nodeInterface.routingTableLock.readLock.unlock
	  option match{
	    case Some((cost, nextAddr)) => {
	      val interface = nodeInterface.virtAddrToInterface.get(nextAddr)
	      interface match{
	        case Some(_interface) =>{
	          if (_interface.isUpOrDown){
	            // Decrease TTL by one
	            packet.head.ttl = (packet.head.ttl - 1).asInstanceOf[Short]
	            _interface.outBuffer.bufferWrite(packet)
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