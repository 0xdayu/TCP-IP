package ip

class Receiving(nodeInterface: NodeInterface) extends Runnable {
  def run() {
    //will repeat until the thread ends
    while (true) {
      nodeInterface.recvPacket
    }
  }
}