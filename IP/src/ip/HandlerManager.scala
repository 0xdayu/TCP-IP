package ip

import scala.collection.mutable.HashMap

class HandlerManager(nodeInterface: NodeInterface) extends Runnable {
  type handlerType = (IPPacket, NodeInterface) => Unit

  val registeredHandlerMap = new HashMap[Int, handlerType]

  def registerHandler(protocolNum: Int, handler: handlerType) {
    registeredHandlerMap.put(protocolNum, handler)
  }

  def run() {
    while (true) {
      for (interface <- nodeInterface.linkInterfaceArray) {
        //TODO: MUTEX LOCK
        if (interface.isUpOrDown && !interface.inBuffer.isEmpty) {
          val pkt = interface.inBuffer.bufferRead
          val option = registeredHandlerMap.get(pkt.head.protocol)
          option match {
            case Some(handler) => handler(pkt, nodeInterface)
            case None => println("No Handler registered for this protocol: " + pkt.head.protocol)
          }
        }
      }
    }
  }
}