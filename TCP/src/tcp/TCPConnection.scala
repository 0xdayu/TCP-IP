package tcp

import tcputil._
import scala.collection.mutable.HashMap
import java.net.InetAddress
import scala.util.Random
import scala.compat.Platform

class TCPConnection(s: Int, p: Int, fb: Int, multiplexingBuff: FIFOBuffer) {

  var srcSocket: Int = s
  var srcPort: Int = p
  var state = TCPState.CLOSE
  var sendBuf: SendBuffer = new SendBuffer(fb)
  var recvBuf: RecvBuffer = new RecvBuffer(fb)

  var dstIP: InetAddress = _
  var dstPort: Int = _

  var seqNum: Long = new Random(Platform.currentTime).nextLong() % math.pow(2, 32).asInstanceOf[Long]
  var ackNum: Long = _

  val previousState = new HashMap[TCPState.Value, Array[TCPState.Value]]
  val nextState = new HashMap[TCPState.Value, Array[TCPState.Value]]

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

  def setState(next: TCPState.Value): Boolean = {
    if (nextState.getOrElse(state, null).contains(next)) {
      state = next
      true
    } else {
      false
    }
  }

  def increaseSeqNumber(a: Int) = {
    seqNum = (seqNum + a) % math.pow(2, 32).asInstanceOf[Long]
  }

  def increaseAckNumber(a: Int) = {
    ackNum = (ackNum + a) % math.pow(2, 32).asInstanceOf[Long]
  }

  def generateAndSentFirstTCPSegment() {
    // generate TCP segment
    val newTCPSegment = new TCPSegment
    val newTCPHead = new TCPHead
    // initial tcp packet
    newTCPHead.srcPort = this.srcPort
    newTCPHead.dstPort = this.dstPort
    newTCPHead.seqNum = this.seqNum
    newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
    newTCPHead.syn = 1
    newTCPHead.winSize = this.recvBuf.available
    // checksum will update later in the ip layer

    newTCPSegment.head = newTCPHead
    newTCPSegment.payLoad = new Array[Byte](0)
    this.multiplexingBuff.bufferWrite(newTCPSegment)
  }

  def generateTCPSegment(seg: TCPSegment, payload: Array[Byte] = new Array[Byte](0)): TCPSegment = {
    val newTCPHead = new TCPHead
    newTCPHead.srcPort = seg.head.dstPort
    newTCPHead.dstPort = seg.head.srcPort
    newTCPHead.seqNum = this.seqNum
    increaseSeqNumber(payload.length + 1)
    increaseAckNumber(payload.length + 1)
    newTCPHead.ackNum = this.ackNum
    newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
    newTCPHead.winSize = this.recvBuf.available
    val newTCPSegment = new TCPSegment
    newTCPSegment.head = newTCPHead
    newTCPSegment.payLoad = payload
    newTCPSegment
  }

  // based on current state, expect proper segment and behave based on state machine
  def connectionBehavior(seg: TCPSegment) {
    state match {
      case TCPState.CLOSE =>
      case TCPState.ESTABLISHED =>
      case TCPState.SYN_SENT =>
        // expect to get segment with syn+ack (3 of 3 handshakes)
        if (seg.head.syn == 1 && seg.head.ack == 1 && seg.head.ackNum == this.seqNum) {
          this.setState(TCPState.ESTABLISHED)

          // TODO : Add payload
          val ackSeg = generateTCPSegment(seg)
          ackSeg.head.ack = 1

          multiplexingBuff.bufferWrite(ackSeg)
        }
      case TCPState.SYN_RECV =>
        if (seg.head.syn == 0 && seg.head.ack == 1 && seg.head.ackNum == this.seqNum) {
          this.setState(TCPState.ESTABLISHED)

          // TODO
        }
      case TCPState.FIN_WAIT1 =>
      case TCPState.FIN_WAIT2 =>
      case TCPState.TIME_WAIT =>
      case TCPState.CLOSE_WAIT =>
      case TCPState.LAST_ACK =>
      case TCPState.LISTEN =>
        if (seg.head.syn == 1) {
          // 2 of 3 handshakes
          this.setState(TCPState.SYN_RECV)
          this.ackNum = seg.head.seqNum

          val newTCPSeg = generateTCPSegment(seg)
          newTCPSeg.head.syn = 1
          newTCPSeg.head.ack = 1

          multiplexingBuff.bufferWrite(newTCPSeg)
        }
      case TCPState.CLOSING =>
      //case _ => throw new UnknownTCPStateException
    }
  }

}