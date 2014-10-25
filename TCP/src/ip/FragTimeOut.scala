package ip

import java.util.TimerTask

class FragTimeOut(nodeInterface: NodeInterface) extends TimerTask {
  def run() {
    nodeInterface.fragPacketLock.writeLock.lock
    if (nodeInterface.fragPacket.isEmpty) {
      nodeInterface.fragTimeOut.schedule(new FragTimeOut(nodeInterface), nodeInterface.TimeFrag)
      nodeInterface.fragPacketLock.writeLock.unlock
      return
    }

    val exp = nodeInterface.fragPacket.head._2._1
    val sysTime = System.currentTimeMillis

    if (exp <= sysTime) {
      nodeInterface.fragPacket.remove(nodeInterface.fragPacket.head._1)

      if (nodeInterface.fragPacket.isEmpty) {
        nodeInterface.fragTimeOut.schedule(new FragTimeOut(nodeInterface), nodeInterface.TimeFrag)
      } else {
        nodeInterface.fragTimeOut.schedule(new FragTimeOut(nodeInterface), math.max(nodeInterface.fragPacket.head._2._1 - sysTime, 0))
      }
    } else {
      nodeInterface.fragTimeOut.schedule(new FragTimeOut(nodeInterface), math.max(exp - sysTime, 0))
    }
    nodeInterface.fragPacketLock.writeLock.unlock
  }
}