package tcp

class ReceivedTCPSegmentHandler(conn: TCPConnection, seg: TCPSegment) extends Runnable{
  
  def run(){
    conn.connectionBehavior(seg)
  }

}