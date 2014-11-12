package tcp

import exception.SocketClosedException

class TCPAccept(tcp: TCP, socket: Int) extends Runnable {
  var done = true
  def run() {
    try {
      while (done) {
        tcp.virAccept(socket)
      }
    } catch {
      case e: SocketClosedException => return
      case e: Exception => println(e.getMessage)
    }
  }

  def cancel() {
    done = false
  }
}