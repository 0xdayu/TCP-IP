package tcp

import tcputil._
import scala.collection.mutable.HashMap
import java.net.InetAddress
import scala.util.Random
import scala.compat.Platform
import scala.collection.mutable.LinkedHashMap
import java.util.concurrent.Semaphore
import java.util.Timer
import java.io.File
import java.io.PrintWriter

class TCPConnection(skt: Int, port: Int, tcp: TCP) {

  var socket: Int = skt

  var state = TCPState.CLOSE

  var checkState: TCPState.Value = null
  val semaphoreCheckState = new Semaphore(0)

  var dstFlowWindow: Int = _

  var sendBuf: SendBuffer = new SendBuffer(tcp.DefaultFlowBuffSize, this)
  var recvBuf: RecvBuffer = new RecvBuffer(tcp.DefaultFlowBuffSize)

  var blockRecv: Boolean = false
  var blockSend: Boolean = false

  var close: Boolean = false

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

  // timeout for connect or close
  var connectOrCloseTimeOut = new Timer

  // timeout
  var dataTimeout = new Timer
  // sequenceNumber, Last set time
  var timeOutRecord: Long = 0

  var dataSendingThread: Thread = _
  var needReply = false

  // flag for close
  var zombie = false

  val previousState = new HashMap[TCPState.Value, Array[TCPState.Value]]
  val nextState = new HashMap[TCPState.Value, Array[TCPState.Value]]

  var dupAckCount: (Long, Int) = (0, 0)

  var noReplyCount = 0

  // ackNumber, sendTime-Stamp
  var rttValidFlag = false
  var rto: Long = tcp.DefaultRTO
  var estRTT: Long = 350
  var seqRecord: Long = _
  var sendRTTRecord: Long = _
  var recvRTTRecord: Long = _

  // congestion control
  var threshold: Int = tcp.DefaultFlowBuffSize
  var cwd: Int = 1 * tcp.DefaultMSS

  // output file for RTO and cwd
  val outputRTO = new PrintWriter(new File("outputRTO"))
  val outputCWD = new PrintWriter(new File("outputCWD"))

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
    // once it changes the state, we need to cancel the timeout
    // maybe it is not necessary for established states
    cancelTimeOut

    // remove itself
    if (next == TCPState.CLOSE) {
      tcp.removeFromTCP(this)
    }

    this.synchronized {
      var ret = false
      if (nextState.getOrElse(state, null).contains(next)) {
        state = next
        if (checkState == next || next == TCPState.CLOSE) {
          semaphoreCheckState.release
          semaphoreQueue.release
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

  def isSynsent(): Boolean = {
    this.synchronized {
      state == TCPState.SYN_SENT
    }
  }

  def isSynrecv: Boolean = {
    this.synchronized {
      state == TCPState.SYN_RECV
    }
  }

  def isEstablished(): Boolean = {
    this.synchronized {
      state == TCPState.ESTABLISHED
    }
  }

  def isFinWait1(): Boolean = {
    this.synchronized {
      state == TCPState.FIN_WAIT1
    }
  }

  def isFinWait2(): Boolean = {
    this.synchronized {
      state == TCPState.FIN_WAIT2
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
      newTCPHead.winSize = this.recvBuf.getSliding
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
      newTCPHead.winSize = this.recvBuf.getSliding
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
        val seg = generateTCPSegment(payload)
        if (!rttValidFlag) {
          this.sendRTTRecord = System.nanoTime
          this.seqRecord = seg.head.seqNum
          this.rttValidFlag = true
        }
        tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, seg)
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
      newTCPHead.winSize = this.recvBuf.getSliding
      // checksum will update later

      // set all the acks
      newTCPHead.ack = 1

      newTCPSegment.head = newTCPHead
      newTCPSegment.payLoad = payload

      newTCPSegment
    }
  }

  // this is for timeout
  def generateTCPSegment(payload: Array[Byte], startSeq: Long, len: Int): TCPSegment = {
    this.synchronized {
      val newTCPSegment = new TCPSegment
      val newTCPHead = new TCPHead
      // initial tcp packet
      newTCPHead.srcPort = this.srcPort
      newTCPHead.dstPort = this.dstPort
      newTCPHead.seqNum = increaseNumber(startSeq, len)
      newTCPHead.ackNum = this.ackNum
      newTCPHead.dataOffset = ConvertObject.DefaultHeadLength
      newTCPHead.winSize = this.recvBuf.getSliding
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
      newTCPHead.winSize = this.recvBuf.getSliding
      val newTCPSegment = new TCPSegment

      newTCPSegment.head = newTCPHead
      newTCPSegment.payLoad = new Array[Byte](0)

      newTCPSegment
    }
  }

  def recvData(seg: TCPSegment) {
    this.synchronized {
      noReplyCount = 0

      var needToResetTime = false
      var isFastRetransmit = false

      // avoid sliding change
      this.recvBuf.synchronized {
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
      }

      // congestion control variable
      var sendLen = 0
      var totalLen = this.sendBuf.getSendLength

      this.sendBuf.synchronized {
        // deal with flight sending data
        var start = this.seqNum
        // here, must be flow window not sliding window by math.min(dstFlowWindow, cwd)
        var end = increaseNumber(start, this.dstFlowWindow)

        if (start <= end && seg.head.ackNum >= start && seg.head.ackNum <= end) {
          val rmLen = (seg.head.ackNum - start).asInstanceOf[Int]

          this.sendBuf.removeFlightData(rmLen)
          this.seqNum = seg.head.ackNum
          if (rmLen != 0) {
            needToResetTime = true
          }

          sendLen = rmLen

          // calculate RTO
          if (this.seqRecord <= seg.head.ackNum) {
            this.calculateRTO
          }
        } else if (start > end && (seg.head.ackNum >= start || seg.head.ackNum <= end)) {
          if (seg.head.ackNum >= start) {
            val rmLen = (seg.head.ackNum - start).asInstanceOf[Int]
            this.sendBuf.removeFlightData(rmLen)
            if (rmLen != 0) {
              needToResetTime = true
            }

            sendLen = rmLen

            // calculate RTO
            if (this.seqRecord <= end) {
              this.calculateRTO
            } else if (this.seqRecord <= seg.head.ackNum) {
              this.calculateRTO
            }
          } else {
            val offset = math.pow(2, 32).asInstanceOf[Long] - start + seg.head.ackNum
            val rmLen = offset.asInstanceOf[Int]
            this.sendBuf.removeFlightData(rmLen)
            if (offset.asInstanceOf[Int] != 0) {
              needToResetTime = true
            }

            sendLen = rmLen

            // calculate RTO
            if (this.seqRecord <= end && this.seqRecord <= seg.head.ackNum) {
              this.calculateRTO
            } else if (this.seqRecord >= start) {
              this.calculateRTO
            }
          }
          this.seqNum = seg.head.ackNum
        }
      }

      // fast retransmit: same variable with needToResetTime
      if (needToResetTime) {
        this.dupAckCount = (seg.head.ackNum, 0)
      } else if (seg.head.ackNum == this.dupAckCount._1) {
        this.dupAckCount = (dupAckCount._1, dupAckCount._2 + 1)
      }

      if (dupAckCount._2 == 4) {
        if (sendBuf.getSendLength != 0) {
          isFastRetransmit = true

          val payload = sendBuf.fastRetransmit(tcp.DefaultMSS)
          if (payload.length != 0) {
            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, generateTCPSegment(payload, this.seqNum, 0))
          }
        }
        this.dupAckCount = (dupAckCount._1, 0)
      }

      if (isFastRetransmit) {
        this.congestionControl(0, 0, 0)
      } else {
        if (totalLen != 0 && sendLen != 0) {
          this.congestionControl(2, sendLen, totalLen)
        }
      }

      // receive the ack increasing or fast retransmit
      if (needToResetTime) {
        // cancel and set new timeout
        dataTimeout.cancel

        this.timeOutRecord = System.currentTimeMillis

        // timeout thread
        dataTimeout = new Timer
        dataTimeout.schedule(new DataTimeOut(tcp, this), rto, rto)
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
      // Maybe we should change
      if (seg.head.rst == 1) {
        println("Attempted to connect, but connection was reset.")
        tcp.virClose(socket)
        return
      }

      dstFlowWindow = seg.head.winSize
      this.sendBuf.setSliding(math.min(dstFlowWindow, cwd))
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

              // wakeup
              this.recvBuf.wakeup
            }
          } else if (seg.head.ack == 1 && seg.head.syn == 1 && seg.head.fin == 0) {
            // ack transmit
            val newSeg = this.replyTCPSegment(seg)
            newSeg.head.ack = 1

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)
          }
        case TCPState.CLOSE_WAIT =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 0) {
            recvData(seg)
          } else if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 1) {
            // ack transmit
            val newSeg = this.replyTCPSegment(seg)
            newSeg.head.ack = 1

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)
          }
        case TCPState.SYN_SENT =>
          // expect to get segment with syn+ack (3 of 3 handshakes)
          if (seg.head.syn == 1 && seg.head.ack == 1 && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.payLoad.length == 0 && seg.head.fin == 0) {
            this.seqNum = increaseNumber(this.seqNum, 1)
            this.ackNum = increaseNumber(seg.head.seqNum, 1)

            // new send thread
            dataSendingThread = new Thread(new DataSending(this))
            dataSendingThread.start

            // timeout thread
            dataTimeout.schedule(new DataTimeOut(tcp, this), rto, rto)

            val ackSeg = replyTCPSegment(seg)
            ackSeg.head.ack = 1

            setState(TCPState.ESTABLISHED)

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, ackSeg)
          } else if (seg.head.syn == 1 && seg.head.ack == 0 && seg.payLoad.length == 0) {
            // simultaneous
            this.ackNum = increaseNumber(seg.head.seqNum, 1)

            val ackSeg = replyTCPSegment(seg)
            ackSeg.head.ack = 1
            ackSeg.head.syn = 1

            setState(TCPState.SYN_RECV)

            val clone = ConvertObject.cloneSegment(ackSeg)
            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, ackSeg)
            this.setTimeOut(clone)
          }
        case TCPState.SYN_RECV =>
          if (seg.head.syn == 0 && seg.head.ack == 1 && seg.head.seqNum == this.ackNum && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.head.fin == 0) {
            this.seqNum = increaseNumber(this.seqNum, 1)

            setState(TCPState.ESTABLISHED)

            this.ackNum = increaseNumber(this.ackNum, this.recvBuf.write(0, seg.payLoad))

            // new send thread
            dataSendingThread = new Thread(new DataSending(this))
            dataSendingThread.start

            // timeout thread
            dataTimeout.schedule(new DataTimeOut(tcp, this), rto, rto)
          } else if (seg.head.syn == 1 && seg.head.ack == 1 && increaseNumber(seg.head.seqNum, 1) == this.ackNum && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.head.fin == 0) {
            // simultaneous
            this.seqNum = increaseNumber(this.seqNum, 1)

            setState(TCPState.ESTABLISHED)

            this.ackNum = increaseNumber(this.ackNum, this.recvBuf.write(0, seg.payLoad))

            // new send thread
            dataSendingThread = new Thread(new DataSending(this))
            dataSendingThread.start

            // timeout thread
            dataTimeout.schedule(new DataTimeOut(tcp, this), rto, rto)
          } else if (seg.head.syn == 1 && seg.head.fin == 0) {
            // ack transmit
            val newSeg = this.replyTCPSegment(seg)
            newSeg.head.ack = 1
            newSeg.head.syn = 1

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)
          }
        case TCPState.FIN_WAIT1 =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.head.seqNum == this.ackNum && seg.head.fin == 0) {
            this.seqNum = increaseNumber(this.seqNum, 1)

            setState(TCPState.FIN_WAIT2)

            recvData(seg)
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

            setState(TCPState.TIME_WAIT)

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)

            timeWait = true
          } else if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 0) {
            recvData(seg)
          }
        case TCPState.TIME_WAIT =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 1) {
            // ack retransmit
            val newSeg = this.replyTCPSegment(seg)
            newSeg.head.ack = 1

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)
          }
        case TCPState.LAST_ACK =>
          if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.ackNum == increaseNumber(this.seqNum, 1) && seg.head.seqNum == this.ackNum && seg.head.fin == 0) {
            this.setState(TCPState.CLOSE)
          }
        case TCPState.LISTEN =>
          if (seg.head.syn == 1 && seg.head.ack == 0 && seg.payLoad.length == 0) {
            if (!pendingQueue.contains((dstip, seg.head.dstPort, srcip, seg.head.srcPort)) && pendingQueue.size <= tcp.PendingQueueSize) {
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
          } else if (seg.head.ack == 1 && seg.head.syn == 0 && seg.head.fin == 1) {
            // ack retransmit
            val newSeg = this.replyTCPSegment(seg)
            newSeg.head.ack = 1

            tcp.multiplexingBuff.bufferWrite(srcIP, dstIP, newSeg)
          }
      }
    }

    // time out to close and remove this connection
    if (timeWait == true) {
      Thread.sleep(2 * tcp.DefaultMSL)

      this.setState(TCPState.CLOSE)
    }
  }

  def setTimeOut(seg: TCPSegment) {
    connectOrCloseTimeOut = new Timer
    connectOrCloseTimeOut.schedule(new ConnectOrCloseTimeOut(tcp, this, seg), tcp.DefaultConnectOrCloseTimeout)
  }

  def cancelTimeOut() {
    connectOrCloseTimeOut.cancel
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

  def getSeq(): Long = {
    this.synchronized {
      seqNum
    }
  }

  def getFlowWindow(): Int = {
    this.synchronized {
      dstFlowWindow
    }
  }

  def calculateRTO() {
    this.synchronized {
      if (this.rttValidFlag) {
        this.recvRTTRecord = System.nanoTime
        val spl = (this.recvRTTRecord - this.sendRTTRecord) / 1000
        this.estRTT = ((1 - 0.125) * this.estRTT + 0.125 * spl).asInstanceOf[Long]
        this.rto = math.max(math.min((this.estRTT * 1.0 / 1000 * 2).asInstanceOf[Int], tcp.DefaultRTOUp), tcp.DefaultRTOLow)
        // println("RTT: " + spl + ", estRTT: " + estRTT + ", RTO: " + rto)
        val str = "RTT: " + spl + ", estRTT: " + estRTT + ", RTO: " + rto + "\n"
        this.outputRTO.write(str, 0, str.length)

        this.rttValidFlag = false
      }
    }
  }

  def congestionControl(congState: Int, sendLen: Int, totalLen: Int) {
    this.synchronized {
      congState match {
        case 0 =>
          {
            // fast retransmit
            this.cwd = math.max((0.5 * this.cwd).asInstanceOf[Int], tcp.DefaultMSS)
            this.threshold = this.cwd
          }
        case 1 =>
          {
            // timeout
            this.threshold = (0.5 * this.cwd).asInstanceOf[Int]
            this.cwd = 1 * tcp.DefaultMSS
          }
        case 2 =>
          {
            // normal case
            if (this.cwd >= this.threshold) {
              val increase = ((sendLen.asInstanceOf[Double] / totalLen) * tcp.DefaultMSS).asInstanceOf[Int]
              this.cwd += increase
            } else {
              this.cwd += sendLen
            }
          }
      }

      // println("cwd: " + cwd)
      val str = cwd.toString + "\n"
      outputCWD.write(str, 0, str.length)

      sendBuf.setSliding(math.min(dstFlowWindow, cwd))
    }
  }
}