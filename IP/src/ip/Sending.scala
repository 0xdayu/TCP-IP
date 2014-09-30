package ip

class Sending(nodeInterface: NodeInterface) extends Runnable {
  def run() {
    //will repeat until the thread ends
    while (true) {
      for (interface <- nodeInterface.linkInterfaceArray) {
        nodeInterface.sendPacket(interface)
      }
    }
  }
}