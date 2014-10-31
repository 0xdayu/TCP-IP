package ip

import scala.collection.mutable.HashMap
import tcp.TCP

class HandlerManager(nodeInterface: NodeInterface, tcp: TCP) extends Runnable {
  type handlerType = (IPPacket, NodeInterface, TCP) => Unit
  var done = true

  val registeredHandlerMap = new HashMap[Int, handlerType]

  def registerHandler(protocolNum: Int, handler: handlerType) {
    registeredHandlerMap.put(protocolNum, handler)
  }

  def run() {
    while (done) {
      for (interface <- nodeInterface.linkInterfaceArray) {
        if (interface.isUpOrDown && !interface.inBuffer.isEmpty) {
          val pkt = interface.inBuffer.bufferRead
          val option = registeredHandlerMap.get(pkt.head.protocol)
          option match {
            case Some(handler) => handler(pkt, nodeInterface, tcp)
            case None => println("No Handler registered for this protocol: " + pkt.head.protocol)
          }
        }
      }
    }
  }

  def cancel() {
    done = false
  }
}