package tcp

class TCPAccept(tcp: TCP, socket: Int) extends Runnable {
  var done = true
  def run() {
    while (done) {
      try {
        tcp.virAccept(socket)
      } catch {
        case e: Exception => println(e.getMessage); return
      }
    }
  }

  def cancel() {
    done = false
  }
}