package ip

import java.util.TimerTask
import java.net.InetAddress
import scala.collection.mutable.ArrayBuffer

class PeriodicUpdate(nodeInterface: NodeInterface) extends TimerTask {
  def run() {
    nodeInterface.routingTableLock.readLock.lock
    for (interface <- nodeInterface.linkInterfaceArray) {
      // response all the tables to request address
      val responseRIP = new RIP
      responseRIP.command = nodeInterface.RIPResponse
      
      val tempEntryArray = new ArrayBuffer[(Int, InetAddress)]

      for (entry <- nodeInterface.routingTable) {
        if (entry._2._2 == interface.getRemoteIP) {
          tempEntryArray += (nodeInterface.RIPInifinity, entry._1).asInstanceOf[(Int, InetAddress)] // cost, destination, poison reverse
        } else {
          tempEntryArray += (entry._2._1, entry._1).asInstanceOf[(Int, InetAddress)] // cost, destination
        }
      }
      
      // Announce all up link interfaces
      for (_interface <- nodeInterface.linkInterfaceArray){
        if (_interface.isUpOrDown && _interface.getLocalIP != interface.getLocalIP){
        	tempEntryArray += (0, _interface.getLocalIP).asInstanceOf[(Int, InetAddress)] // cost, destination	
        }
      }
      responseRIP.numEntries = tempEntryArray.length
      responseRIP.entries = tempEntryArray.toArray

      nodeInterface.ripResponse(interface.getRemoteIP, responseRIP)
    }
    nodeInterface.routingTableLock.readLock.unlock
  }
}