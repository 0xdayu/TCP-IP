package tcp

class DataSending(conn: TCPConnection) extends Runnable {
  var done = true

  def run() {
    while (done) {
    	
    }
  }

  def cancel() {
    done = false
  }
}