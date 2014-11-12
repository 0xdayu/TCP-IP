package tcp

import tcputil._
import scala.collection.mutable.HashMap
import java.net.InetAddress
import scala.util.Random
import scala.compat.Platform
import scala.collection.mutable.LinkedHashMap
import java.util.concurrent.Semaphore

class TCPConnection(skt: Int, port: Int, tcp: TCP) {

  var socket: Int = skt

  var state = TCPState.CLOSE

  var checkState: TCPState.Value = null
  val semaphoreCheckState = new Semaphore(0)

  var sendBuf: SendBuffer = new SendBuffer(tcp.DefaultFlowBuffSize, tcp.DefaultSlidingWindow, this)
  var recvBuf: RecvBuffer = new RecvBuffer(tcp.DefaultFlowBuffSize, tcp.DefaultSlidingWindow)

  // src
  var srcIP: InetAddress = _
  var srcPort: Int = port

  // dst
  var dstIP: InetAddress = _
  var dstPort: Int = _

  var seqNum: Long = (math.abs(new Random(Platform.currentTime).nextLong()) % math.pow(2, 32).asInstanceOf[Long]).asInstanceOf[Long]
  var ackNum: Long = _

  val pendingQueue = new LinkedHashMap[(InetAddress, Int, InetAddress, Int), TCPConnection]
  val semaphoreQueue = new Semaphore(0)

  val PendingQueueSize = 65535

  var dataSendingThread: Thread = _
  var needReply = false

  // flag for close
  var zombie = false

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
  nextState.put(TCPState.LISTEN, Array(TCPState.SYN_RECV, TCPState.SYN_SENT, TCPState.CLOSE))
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
    // remove itself
    if (next == TCPState.CLOSE) {
      tcp.removeFromTCP(this)
    }

    this.synchronized {
      var ret = false
      if (nextState.getOrElse(state, null).contains(next)) {
        state = next
        if (checkState == next) {
          semaphoreCheckState.release
          // back to null
          checkState = null
        }
        ret = true
      } else {
        ret = false
      }
      ret
    }
  }

  def getState(): TCPState.Value = {
    this.synchronized {
      state
    }
  }

  // set wait state before sending segment
  def setWaitState(waitState: TCPState.Value) {
    this.synchronized {
      checkState = waitState
    }
  }

  // wait that setting state after sending segment
  def waitState() {
    semaphoreCheckState.acquire
  }

  def increaseNumber(num: Long, a: Int): Long = {
    (num + a.asInstanceOf[Long]) % math.pow(2, 32).asInstanceOf[Long]
  }

  def isServerAndListen(): Boolean = {
    this.synchronized {
      state == TCPState.LISTEN
    }
  }

  def isEstablished(): Boolean = {
    this.synchronized {
      state == TCPState.ESTABLISHED
    }
  }

  def isCloseWait(): Boolean = {
    this.synchronized {
      state == TCPState.CLOSE_WAIT
    }
  }

  // user generate
  def generateFirstTCPSegment(): TCPSegment = {
    this.synchronized {
      // generate TCP segment
      val newTCPSegment = new TCPSegment
      val newTCPHead = new TCPHead
      // initial tcp packet
      newTCPHead.srcPort = this.srcPort
      newTCPHead.dstPort = this.dstPort
      newTCPHead.seqNum = this.seqNum
      newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
      newTCPHead.syn = 1
      newTCPHead.winSize = this.recvBuf.getAvailable
      // checksum will update later

      newTCPSegment.head = newTCPHead
      newTCPSegment.payLoad = new Array[Byte](0)

      newTCPSegment
    }
  }

  // user generate
  def generateTCPSegment(): TCPSegment = {
    this.synchronized {
      val newTCPSegment = new TCPSegment
      val newTCPHead = new TCPHead
      // initial tcp packet
      newTCPHead.srcPort = this.srcPort
      newTCPHead.dstPort = this.dstPort
      newTCPHead.seqNum = this.seqNum
      newTCPHead.ackNum = this.ackNum
      newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
      newTCPHead.winSize = this.recvBuf.getAvailable
      // checksum will update later

      newTCPSegment.head = newTCPHead
      newTCPSegment.payLoad = new Array[Byte](0)

      newTCPSegment
    }
  }

  def dataSend() {
    this.synchronized {
      val payload = sendBuf.read(tcp.DefaultMSS)
      if (payload.length != 0 || needReply) {
        tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, generateTCPSegment(payload))
        needReply = false
      } else {
        this.wait
      }
    }
  }

  def wakeUpDataSend() {
    this.synchronized {
      this.notify
    }
  }

  def generateTCPSegment(payload: Array[Byte]): TCPSegment = {
    this.synchronized {
      val newTCPSegment = new TCPSegment
      val newTCPHead = new TCPHead
      // initial tcp packet
      newTCPHead.srcPort = this.srcPort
      newTCPHead.dstPort = this.dstPort
      newTCPHead.seqNum = increaseNumber(this.seqNum, this.sendBuf.getSendLength - payload.length)
      newTCPHead.ackNum = this.ackNum
      newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
      newTCPHead.winSize = this.recvBuf.getAvailable
      // checksum will update later

      // set all the acks
      newTCPHead.ack = 1

      newTCPSegment.head = newTCPHead
      newTCPSegment.payLoad = payload

      newTCPSegment
    }
  }

  def replyTCPSegment(seg: TCPSegment): TCPSegment = {
    this.synchronized {
      val newTCPHead = new TCPHead
      newTCPHead.srcPort = seg.head.dstPort
      newTCPHead.dstPort = seg.head.srcPort
      newTCPHead.seqNum = this.seqNum
      newTCPHead.ackNum = this.ackNum
      newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
      newTCPHead.winSize = this.recvBuf.getAvailable
      val newTCPSegment = new TCPSegment

      newTCPSegment.head = newTCPHead
      newTCPSegment.payLoad = new Array[Byte](0)

      newTCPSegment
    }
  }

  def recvData(seg: TCPSegment) {
    this.synchronized {
      // receive the data
      var start = this.ackNum
      var end = increaseNumber(start, this.recvBuf.getSliding)

      if (start <= end && seg.head.seqNum >= start && seg.head.seqNum <= end) {
        this.ackNum = increaseNumber(this.ackNum, this.recvBuf.write((seg.head.seqNum - start).asInstanceOf[Int], seg.payLoad))
      } else if (start > end && (seg.head.seqNum >= start || seg.head.seqNum <= end)) {
        if (seg.head.seqNum >= start) {
          this.ackNum = increaseNumber(this.ackNum, this.recvBuf.write((seg.head.seqNum - start).asInstanceOf[Int], seg.payLoad))
        } else {
          val offset = math.pow(2, 32).asInstanceOf[Long] - start + seg.head.seqNum
          this.ackNum = increaseNumber(this.ackNum, this.recvBuf.write(offset.asInstanceOf[Int], seg.payLoad))
        }
      }

      // deal with flight sending data
      start = this.seqNum
      end = increaseNumber(start, this.sendBuf.getSliding)
      if (start <= end && seg.head.ackNum >= start && seg.head.ackNum <= end) {
        this.sendBuf.removeFlightData((seg.head.ackNum - start).asInstanceOf[Int])
        this.seqNum = seg.head.ackNum
      } else if (start > end && (seg.head.ackNum >= start || seg.head.seqNum <= end)) {
        if (seg.head.ackNum >= start) {
          this.sendBuf.removeFlightData((seg.head.ackNum - start).asInstanceOf[Int])
        } else {
          val offset = math.pow(2, 32).asInstanceOf[Long] - start + seg.head.ackNum
          this.sendBuf.removeFlightData(offset.asInstanceOf[Int])
        }
        this.seqNum = seg.head.ackNum
      }

      if (seg.payLoad.length != 0) {
        // receive the segment and notify
        needReply = true
        this.wakeUpDataSend
      }
    }
  }

  // based on current state, expect proper segment and behave based on state machine
  def connectionBehavior(srcip: InetAddress, dstip: InetAddress, seg: TCPSegment) {
    var timeWait = false

    this.synchronized {
      state match {
        case TCPState.CLOSE =>
        case TCPState.ESTABLISHED =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 0) {
            recvData(seg)
          } else if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 1) {
            if (seg.head.ackNum == this.seqNum && seg.head.seqNum == this.ackNum) {
              this.ackNum = increaseNumber(this.ackNum, 1)

              val newSeg = this.replyTCPSegment(seg)
              newSeg.head.ack = 1

              tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)

              setState(TCPState.CLOSE_WAIT)
            }
          }
        case TCPState.CLOSE_WAIT =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 0) {
            recvData(seg)

            if (sendBuf.isEmpty) {

              val newSeg = replyTCPSegment(seg)
              newSeg.head.fin = 1
              newSeg.head.ack = 1

              tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)

              this.dataSendingThread.stop

              setState(TCPState.LAST_ACK)
            }
          }
        case TCPState.SYN_SENT =>
          // expect to get segment with syn+ack (3 of 3 handshakes)
          if (seg.head.syn == 1 && seg.head.ack == 1 && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.payLoad.length == 0 && seg.head.fin == 0) {
            this.seqNum = increaseNumber(this.seqNum, 1)
            this.ackNum = increaseNumber(seg.head.seqNum, 1)

            // new send thread
            dataSendingThread = new Thread(new DataSending(this))
            dataSendingThread.start

            val ackSeg = replyTCPSegment(seg)
            ackSeg.head.ack = 1

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, ackSeg)

            setState(TCPState.ESTABLISHED)
          }
        case TCPState.SYN_RECV =>
          if (seg.head.syn == 0 && seg.head.ack == 1 && seg.head.seqNum == this.ackNum && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.head.fin == 0) {
            this.seqNum = increaseNumber(this.seqNum, 1)

            setState(TCPState.ESTABLISHED)

            this.ackNum = increaseNumber(this.ackNum, this.recvBuf.write(0, seg.payLoad))

            // new send thread
            dataSendingThread = new Thread(new DataSending(this))
            dataSendingThread.start
          }
        case TCPState.FIN_WAIT1 =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.head.seqNum == this.ackNum && seg.head.fin == 0) {
            this.seqNum = increaseNumber(this.seqNum, 1)

            setState(TCPState.FIN_WAIT2)

            this.ackNum = increaseNumber(this.ackNum, this.recvBuf.write(0, seg.payLoad))
          } else if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.ackNum == this.seqNum && seg.head.seqNum == this.ackNum && seg.head.fin == 1) {
            // simultanious
            setState(TCPState.CLOSING)

            this.seqNum = increaseNumber(this.seqNum, 1)
            this.ackNum = increaseNumber(this.ackNum, 1)

            val newSeg = this.replyTCPSegment(seg)
            newSeg.head.ack = 1

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)

          } else if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.head.seqNum == this.ackNum && seg.head.fin == 1) {

            this.seqNum = increaseNumber(this.seqNum, 1)
            this.ackNum = increaseNumber(this.ackNum, 1)

            setState(TCPState.TIME_WAIT)

            timeWait = true
          } else if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 0) {
            recvData(seg)
          }
        case TCPState.FIN_WAIT2 =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.ackNum == this.seqNum && seg.head.seqNum == this.ackNum && seg.head.fin == 1) {

            this.ackNum = increaseNumber(this.ackNum, 1)

            val newSeg = this.replyTCPSegment(seg)
            newSeg.head.ack = 1

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)

            setState(TCPState.TIME_WAIT)

            timeWait = true
          } else if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 0) {
            recvData(seg)
          }
        case TCPState.TIME_WAIT =>
          {
            // do nothing
          }
        case TCPState.LAST_ACK =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.head.seqNum == this.ackNum && seg.head.fin == 0) {
            this.setState(TCPState.CLOSE)
          }
        case TCPState.LISTEN =>
          if (seg.head.syn == 1 && seg.payLoad.length == 0 && seg.payLoad.length == 0) {
            if (!pendingQueue.contains((dstip, seg.head.dstPort, srcip, seg.head.srcPort)) && pendingQueue.size <= this.PendingQueueSize) {
              val conn = new TCPConnection(-1, seg.head.dstPort, tcp)
              conn.dstIP = srcip
              conn.dstPort = seg.head.srcPort
              conn.srcIP = dstip

              conn.ackNum = increaseNumber(seg.head.seqNum, 1)

              conn.setState(TCPState.LISTEN)

              pendingQueue.put((dstip, seg.head.dstPort, srcip, seg.head.srcPort), conn)
              semaphoreQueue.release
            } // ignore for else
          }
        case TCPState.CLOSING =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.ackNum == this.seqNum && seg.head.seqNum == this.ackNum && seg.head.fin == 0) {
            setState(TCPState.TIME_WAIT)

            timeWait = true
          }
        //case _ => throw new UnknownTCPStateException
      }
    }

    // time out to close and remove this connection
    if (timeWait == true) {
      Thread.sleep(2 * tcp.DefaultMSL)

      this.setState(TCPState.CLOSE)
    }
  }

  // set and get functions as follows

  def setSocket(s: Int) {
    this.synchronized {
      socket = s
    }
  }

  def getSocket(): Int = {
    this.synchronized {
      socket
    }
  }

  def setSrcIP(srcip: InetAddress) {
    this.synchronized {
      srcIP = srcip
    }
  }

  def getSrcIP(): InetAddress = {
    this.synchronized {
      srcIP
    }
  }

  def setDstIP(dstip: InetAddress) {
    this.synchronized {
      dstIP = dstip
    }
  }

  def getDstIP(): InetAddress = {
    this.synchronized {
      dstIP
    }
  }

  def setSrcPort(srcport: Int) {
    this.synchronized {
      srcPort = srcport
    }
  }

  def getSrcPort(): Int = {
    this.synchronized {
      srcPort
    }
  }

  def setDstPort(dstport: Int) {
    this.synchronized {
      dstPort = dstport
    }
  }

  def getDstPort(): Int = {
    this.synchronized {
      dstPort
    }
  }

  def getAck(): Long = {
    this.synchronized {
      ackNum
    }
  }
}