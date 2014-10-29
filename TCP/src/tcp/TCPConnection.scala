package tcp

import tcputil.TCPState
import scala.collection.mutable.HashMap
import java.net.InetAddress
import scala.util.Random
import scala.compat.Platform
import tcputil.CircularArray

class TCPConnection(s: Int, p: Int, fb: Int) {

  var srcSocket: Int = s
  var srcPort: Int = p
  var state = TCPState.CLOSE
  var sendBuf: CircularArray = new CircularArray(fb)
  var recvBuf: CircularArray = new CircularArray(fb)
  // TODO: 
  //var slidingWinow: Array[Byte] = new Array[Byte](sw)

  var dstIP: InetAddress = _
  var dstPort: Int = _

  var seqNum: Long = new Random(Platform.currentTime).nextLong() % math.pow(2, 32).asInstanceOf[Long]
  var ackNum: Long = _

  val previousState = new HashMap[TCPState.Value, Array[TCPState.Value]]
  val nextState = new HashMap[TCPState.Value, Array[TCPState.Value]]
  
  // sliding window
  
  
  // flow window

  // Hardcode previous and next network state
  previousState.put(TCPState.CLOSE, Array(TCPState.SYN_SENT, TCPState.LISTEN, TCPState.LAST_ACK, TCPState.TIME_WAIT))
  previousState.put(TCPState.LISTEN, Array(TCPState.CLOSE, TCPState.SYN_RECV))
  previousState.put(TCPState.SYN_RECV, Array(TCPState.LISTEN, TCPState.SYN_SENT))
  previousState.put(TCPState.SYN_SENT, Array(TCPState.CLOSE, TCPState.LISTEN))
  previousState.put(TCPState.ESTABLISHED, Array(TCPState.SYN_RECV, TCPState.SYN_SENT))
  previousState.put(TCPState.FIN_WAIT1, Array(TCPState.ESTABLISHED, TCPState.SYN_RECV))
  previousState.put(TCPState.FIN_WAIT2, Array(TCPState.FIN_WAIT1))
  previousState.put(TCPState.CLOSING, Array(TCPState.FIN_WAIT1))
  previousState.put(TCPState.TIME_WAIT, Array(TCPState.FIN_WAIT1, TCPState.FIN_WAIT2, TCPState.CLOSING))
  previousState.put(TCPState.CLOSE_WAIT, Array(TCPState.ESTABLISHED))
  previousState.put(TCPState.LAST_ACK, Array(TCPState.CLOSE_WAIT))

  nextState.put(TCPState.CLOSE, Array(TCPState.LISTEN, TCPState.SYN_SENT))
  nextState.put(TCPState.LISTEN, Array(TCPState.SYN_RECV, TCPState.SYN_SENT))
  nextState.put(TCPState.SYN_RECV, Array(TCPState.LISTEN, TCPState.FIN_WAIT1, TCPState.ESTABLISHED))
  nextState.put(TCPState.SYN_SENT, Array(TCPState.CLOSE, TCPState.SYN_RECV, TCPState.ESTABLISHED))
  nextState.put(TCPState.ESTABLISHED, Array(TCPState.FIN_WAIT1, TCPState.CLOSE_WAIT))
  nextState.put(TCPState.FIN_WAIT1, Array(TCPState.CLOSING, TCPState.FIN_WAIT2, TCPState.TIME_WAIT))
  nextState.put(TCPState.FIN_WAIT2, Array(TCPState.TIME_WAIT))
  nextState.put(TCPState.CLOSING, Array(TCPState.TIME_WAIT))
  nextState.put(TCPState.TIME_WAIT, Array(TCPState.CLOSE))
  nextState.put(TCPState.CLOSE_WAIT, Array(TCPState.LAST_ACK))
  nextState.put(TCPState.LAST_ACK, Array(TCPState.CLOSE))

  def setState(next: TCPState.Value, curr: TCPState.Value): Boolean = {
    if (curr != state) {
      false
    } else {
      if (nextState.getOrElse(state, null).contains(next)) {
        state = next
        true
      } else {
        false
      }
    }
  }

  def increaseSeqNumber(a: Int) = {
    seqNum = (seqNum + a) % math.pow(2, 32).asInstanceOf[Long]
  }
  
  def increaseAckNumber(a: Int) = {
    ackNum = (ackNum + a) % math.pow(2, 32).asInstanceOf[Long]
  }

  //  def setState(s: TCPState.Value) {
  //    s match {
  //      case TCPState.CLOSE =>
  //      case TCPState.ESTABLISHED =>
  //      case TCPState.SYN_SENT =>
  //      case TCPState.SYN_RECV =>
  //      case TCPState.FIN_WAIT1 =>
  //      case TCPState.FIN_WAIT2 =>
  //      case TCPState.TIME_WAIT =>
  //      case TCPState.CLOSE_WAIT =>
  //      case TCPState.LAST_ACK =>
  //      case TCPState.LISTEN =>
  //      case TCPState.CLOSING =>
  //      case _ => throw new UnknownTCPStateException
  //    }
  //  }
}