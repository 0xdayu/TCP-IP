package ip

class Receiving(nodeInterface: NodeInterface) extends Runnable {
  var done = true
  
  def run() {
    //will repeat until the thread ends
    while (done) {
      nodeInterface.recvPacket
    }
  }

  def cancel() {
    done = false
  }
}