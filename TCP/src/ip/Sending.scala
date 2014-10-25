package ip

class Sending(nodeInterface: NodeInterface) extends Runnable {
  var done = true

  def run() {
    //will repeat until the thread ends
    while (done) {
      for (interface <- nodeInterface.linkInterfaceArray) {
        if (interface.isUpOrDown) {
          nodeInterface.sendPacket(interface)
        }
      }
    }
  }

  def cancel() {
    done = false
  }
}