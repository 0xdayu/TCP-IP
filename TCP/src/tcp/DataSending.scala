package tcp

class DataSending(conn: TCPConnection) extends Runnable {
  var done = true

  def run() {
    while (done) {
      conn.dataSend
    }
  }

  def cancel() {
    done = false
  }
}