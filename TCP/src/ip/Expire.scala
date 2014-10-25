package ip

import java.util.TimerTask
import java.net.InetAddress

class Expire(nodeInterface: NodeInterface) extends TimerTask {
  def run() {
    nodeInterface.entryExpireLock.readLock.lock
    if (nodeInterface.entryExpire.isEmpty) {
      nodeInterface.expire.schedule(new Expire(nodeInterface), nodeInterface.TimeExpire)
      nodeInterface.entryExpireLock.readLock.unlock
      return
    }

    val exp = nodeInterface.entryExpire.head._2
    val sysTime = System.currentTimeMillis
    nodeInterface.entryExpireLock.readLock.unlock

    if (exp <= sysTime) {
      // remove from entry expire queue
      nodeInterface.entryExpireLock.writeLock.lock
      val deletedEntry = nodeInterface.entryExpire.head
      nodeInterface.entryExpire.remove(deletedEntry._1)
      nodeInterface.entryExpireLock.writeLock.unlock

      // remove from routing table
      nodeInterface.routingTableLock.writeLock.lock
      nodeInterface.routingTable.remove(deletedEntry._1)
      nodeInterface.routingTableLock.writeLock.unlock

      // tell all the interfaces with inifinity entry
      val deleteRIP = new RIP
      deleteRIP.command = nodeInterface.RIPResponse
      deleteRIP.numEntries = 1
      deleteRIP.entries = new Array[(Int, InetAddress)](1)
      deleteRIP.entries(0) = (nodeInterface.RIPInifinity, deletedEntry._1)

      for (interface <- nodeInterface.linkInterfaceArray) {
        nodeInterface.ripResponse(interface.getRemoteIP, deleteRIP)
      }

      // set time for next entry
      nodeInterface.entryExpireLock.readLock.lock
      if (nodeInterface.entryExpire.isEmpty) {
        nodeInterface.expire.schedule(new Expire(nodeInterface), nodeInterface.TimeExpire)
      } else {
        nodeInterface.expire.schedule(new Expire(nodeInterface), math.max(nodeInterface.entryExpire.head._2 - sysTime, 0))
      }
      nodeInterface.entryExpireLock.readLock.unlock

    } else {
      nodeInterface.expire.schedule(new Expire(nodeInterface), math.max(exp - sysTime, 0))
    }
  }
}