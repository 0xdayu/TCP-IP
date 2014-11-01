package tcp

import tcputil._
import scala.collection.mutable.HashMap
import java.net.InetAddress
import scala.util.Random
import scala.compat.Platform
import scala.collection.mutable.LinkedHashMap
import scala.actors.threadpool.locks.ReentrantReadWriteLock

class TCPConnection(s: Int, p: Int, fb: Int, tcp: TCP) {

  var socket: Int = s

  var state = TCPState.CLOSE
  val stateLock = new ReentrantReadWriteLock

  var sendBuf: SendBuffer = new SendBuffer(fb)
  var recvBuf: RecvBuffer = new RecvBuffer(fb)

  // src
  var srcIP: InetAddress = _
  var srcPort: Int = p

  // dst
  var dstIP: InetAddress = _
  var dstPort: Int = _

  var seqNum: Long = (math.abs(new Random(Platform.currentTime).nextLong()) % math.pow(2, 32).asInstanceOf[Long]).asInstanceOf[Long]
  var ackNum: Long = _

  val pendingQueue = new LinkedHashMap[(InetAddress, Int, InetAddress, Int), TCPConnection]

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
    var ret = false
    stateLock.writeLock.lock
    if (nextState.getOrElse(state, null).contains(next)) {
      state = next
      ret = true
    } else {
      ret = false
    }
    stateLock.writeLock.unlock
    ret
  }

  def getState(): TCPState.Value = {
    stateLock.readLock.lock
    val current = state
    stateLock.readLock.unlock
    current
  }

  def increaseSeqNumber(a: Int) = {
    seqNum = (seqNum + a.asInstanceOf[Long]) % math.pow(2, 32).asInstanceOf[Long]
  }

  def increaseAckNumber(a: Int) = {
    ackNum = (ackNum + a.asInstanceOf[Long]) % math.pow(2, 32).asInstanceOf[Long]
  }

  def isServerAndListen(): Boolean = {
    state == TCPState.LISTEN
  }

  def generateAndSentFirstTCPSegment() {
    // generate TCP segment
    val newTCPSegment = new TCPSegment
    val newTCPHead = new TCPHead
    // initial tcp packet
    newTCPHead.srcPort = this.srcPort
    newTCPHead.dstPort = this.dstPort
    newTCPHead.seqNum = this.seqNum
    increaseSeqNumber(1)
    newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
    newTCPHead.syn = 1
    newTCPHead.winSize = this.recvBuf.available
    // checksum will update later in the ip layer

    newTCPSegment.head = newTCPHead
    newTCPSegment.payLoad = new Array[Byte](0)
    tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newTCPSegment)
  }

  def generateTCPSegment(): TCPSegment = {
    val newTCPSegment = new TCPSegment
    val newTCPHead = new TCPHead
    // initial tcp packet
    newTCPHead.srcPort = this.srcPort
    newTCPHead.dstPort = this.dstPort
    newTCPHead.seqNum = this.seqNum
    increaseSeqNumber(1)
    increaseAckNumber(1)
    newTCPHead.ackNum = this.ackNum
    newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
    newTCPHead.winSize = this.recvBuf.available
    // checksum will update later in the ip layer

    newTCPSegment.head = newTCPHead
    newTCPSegment.payLoad = new Array[Byte](0)
    newTCPSegment
  }

  def replyTCPSegment(seg: TCPSegment): TCPSegment = {
    val newTCPHead = new TCPHead
    newTCPHead.srcPort = seg.head.dstPort
    newTCPHead.dstPort = seg.head.srcPort
    newTCPHead.seqNum = this.seqNum
    increaseSeqNumber(1)
    increaseAckNumber(1)
    newTCPHead.ackNum = this.ackNum
    newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
    newTCPHead.winSize = this.recvBuf.available
    val newTCPSegment = new TCPSegment
    newTCPSegment.head = newTCPHead
    newTCPSegment.payLoad = new Array[Byte](0)
    newTCPSegment
  }

  // based on current state, expect proper segment and behave based on state machine
  def connectionBehavior(srcip: InetAddress, dstip: InetAddress, seg: TCPSegment) {
    state match {
      case TCPState.CLOSE =>
      case TCPState.ESTABLISHED =>
      case TCPState.SYN_SENT =>
        // expect to get segment with syn+ack (3 of 3 handshakes)
        if (seg.head.syn == 1 && seg.head.ack == 1 && seg.head.ackNum == this.seqNum) {
          this.setState(TCPState.ESTABLISHED)

          // TODO : Add payload
          this.ackNum = seg.head.seqNum

          val ackSeg = replyTCPSegment(seg)
          ackSeg.head.ack = 1

          tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, ackSeg)
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
          if (!pendingQueue.contains((dstip, seg.head.dstPort, srcip, seg.head.srcPort))) {
            val conn = new TCPConnection(-1, seg.head.dstPort, tcp.DefaultFlowBuffSize, tcp)
            conn.dstIP = srcip
            conn.dstPort = seg.head.srcPort
            conn.srcIP = dstip

            conn.ackNum = seg.head.seqNum

            conn.setState(TCPState.LISTEN)

            pendingQueue.put((dstip, seg.head.dstPort, srcip, seg.head.srcPort), conn)
          } // ignore for else
        }
      case TCPState.CLOSING =>
      //case _ => throw new UnknownTCPStateException
    }
  }

}