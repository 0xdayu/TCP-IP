package ip

import java.util.TimerTask
import java.net.InetAddress

class PeriodicUpdate(nodeInterface: NodeInterface) extends TimerTask {
  def run() {
    nodeInterface.routingTableLock.readLock.lock
    for (interface <- nodeInterface.linkInterfaceArray) {
      // response all the tables to request address
      val responseRIP = new RIP
      responseRIP.command = nodeInterface.RIPResponse
      responseRIP.numEntries = nodeInterface.routingTable.size
      responseRIP.entries = new Array[(Int, InetAddress)](nodeInterface.routingTable.size)

      var i = 0
      for (entry <- nodeInterface.routingTable) {
        if (entry._2._2 == interface.getRemoteIP) {
          responseRIP.entries(i) = (nodeInterface.RIPInifinity, entry._1) // cost, destination, poison reverse
        } else {
          responseRIP.entries(i) = (entry._2._1, entry._1) // cost, destination
        }
        i += 1
      }

      nodeInterface.ripResponse(interface.getRemoteIP, responseRIP)
    }
    nodeInterface.routingTableLock.readLock.unlock
  }
}