package tcp

import tcputil._
import exception._
import java.net.InetAddress
import scala.collection.mutable.{ HashMap, SynchronizedMap }
import scala.util.Random
import java.util.Collections

class TCP(nodeInterface: ip.NodeInterface) {
  // file descriptor, 0 - input, 1 - output, 2 - error
  // start from 3 to 65535 (2^16 - 1) or less

  // 2^16 - 1
  val DefaultFlowBuffSize = 64 * 1024 - 1
  val DefaultMultiplexingBuffSize = 10 * 1024 * 1024

  // less than tcp segment window
  val DefaultSlidingWindow = 10 * 1024
  // less than mtu
  val DefaultMSS = 1024

  // (ms) 
  val DefaultMSL = 10 * 1000

  val socketLeftBound = 3
  val socketRightBound = 65535

  val portLeftBound = 1024
  val portRightBound = 65535

  // one socket to one connection
  val socketArray = new SynBitSet
  val boundedSocketHashMap = new HashMap[Int, TCPConnection] with SynchronizedMap[Int, TCPConnection]

  // port number, start from 1024 to 65535 (2^16 - 1)
  val portArray = new SynBitSet

  // port -> connection
  // (srcip, srcport, dstip, dstport) -> connection
  val serverHashMap = new HashMap[Int, TCPConnection] with SynchronizedMap[Int, TCPConnection]
  val clientHashMap = new HashMap[(InetAddress, Int, InetAddress, Int), TCPConnection] with SynchronizedMap[(InetAddress, Int, InetAddress, Int), TCPConnection]

  val multiplexingBuff = new FIFOBuffer(DefaultMultiplexingBuffSize)
  val demultiplexingBuff = new FIFOBuffer(DefaultMultiplexingBuffSize)

  def virSocket(): Int = {
    this.synchronized {
      for (i <- Range(socketLeftBound, socketRightBound + 1)) {
        if (!socketArray.get(i)) {
          socketArray.set(i)
          return i
        }
      }

      throw new SocketUsedUpException
    }
  }

  def virBind(socket: Int, addr: InetAddress, port: Int) {
    this.synchronized {
      // addr is not used in virBind
      // check whether socket has been asigned
      if (socket < socketLeftBound || socket > socketRightBound) {
        throw new InvalidSocketException(socket)
      } else if (!socketArray.get(socket)) {
        throw new UninitialSocketException(socket)
      } else if (boundedSocketHashMap.contains(socket)) {
        throw new BoundedSocketException(socket)
      } else if (port < portLeftBound || port > portRightBound) {
        throw new InvalidPortException(port)
      } else if (portArray.get(port)) {
        throw new UsedPortException(port)
      } else {
        portArray.set(port)
        val newCon = new TCPConnection(socket, port, this)
        boundedSocketHashMap.put(socket, newCon)
      }
    }
  }

  def virListen(socket: Int) {
    this.synchronized {
      if (socket < socketLeftBound || socket > socketRightBound) {
        throw new InvalidSocketException(socket)
      } else if (!socketArray.get(socket)) {
        throw new UninitialSocketException(socket)
      } else if (!boundedSocketHashMap.contains(socket)) {
        throw new UnboundSocketException(socket)
      } else {
        val conn = boundedSocketHashMap.getOrElse(socket, null)

        if (conn.zombie) {
          throw new ErrorTCPStateException(conn.getState)
        }

        if (!conn.setState(TCPState.LISTEN)) {
          throw new ErrorTCPStateException(conn.getState)
        } else {
          serverHashMap.put(conn.getSrcPort, conn)
        }
      }
    }
  }

  def virConnect(socket: Int, addr: InetAddress, port: Int) {
    val ip = nodeInterface.getSrcAddr(addr)
    if (ip == null) {
      throw new DestinationUnreachable(addr)
    }

    this.synchronized {
      if (socket < socketLeftBound || socket > socketRightBound) {
        throw new InvalidSocketException(socket)
      } else if (!socketArray.get(socket)) {
        throw new UninitialSocketException(socket)
      } else if (!boundedSocketHashMap.contains(socket)) {
        // no this socket and randomly generate
        val portNumber = generatePortNumber
        if (portNumber == -1) {
          throw new PortUsedUpException
        }
        portArray.set(portNumber)
        val newCon = new TCPConnection(socket, portNumber, this)
        boundedSocketHashMap.put(socket, newCon)
      }
    }

    // initial all parameters for the TCP connection
    if (port < portLeftBound || port > portRightBound) {
      throw new InvalidPortException(port)
    } else {
      var conn: TCPConnection = null
      this.synchronized {
        // 1 of 3 3-way handshake
        conn = boundedSocketHashMap.getOrElse(socket, null)

        if (conn.zombie) {
          throw new ErrorTCPStateException(conn.getState)
        }

        conn.setSrcIP(ip)
        conn.setDstIP(addr)
        conn.setDstPort(port)

        clientHashMap.put((conn.getSrcIP, conn.getSrcPort, conn.getDstIP, conn.getDstPort), conn)
      }

      // set wait state
      conn.setWaitState(TCPState.ESTABLISHED)

      multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, conn.generateFirstTCPSegment)

      // finish 1 of 3 3-way handshake
      conn.setState(TCPState.SYN_SENT)

      // wait that state
      conn.waitState

      // println("Established connection succesfully")
    }
  }

  // return (socket, dstport, dstip)
  def virAccept(socket: Int): (Int, Int, InetAddress) = {
    var conn: TCPConnection = null
    this.synchronized {
      if (socket < socketLeftBound || socket > socketRightBound) {
        throw new InvalidSocketException(socket)
      } else if (!socketArray.get(socket)) {
        throw new UninitialSocketException(socket)
      } else if (!boundedSocketHashMap.contains(socket)) {
        throw new UnboundSocketException(socket)
      }
      conn = boundedSocketHashMap.getOrElse(socket, null)

      if (conn.zombie) {
        throw new ErrorTCPStateException(conn.getState)
      }
    }
    // remove from queue
    conn.semaphoreQueue.acquire

    if (conn.isServerAndListen) {

      var tuple: (InetAddress, Int, InetAddress, Int) = null
      var newConn: TCPConnection = null
      conn.synchronized {
        tuple = conn.pendingQueue.head._1
        newConn = conn.pendingQueue.head._2
        conn.pendingQueue.remove(tuple)
      }

      // assign new socket
      val newSocket = virSocket
      newConn.setSocket(newSocket)

      this.synchronized {
        // put into map
        boundedSocketHashMap.put(newSocket, newConn)
        clientHashMap.put(tuple, newConn)
      }

      newConn.setState(TCPState.SYN_RECV)

      // set wait state
      newConn.setWaitState(TCPState.ESTABLISHED)

      val seg = newConn.generateTCPSegment

      seg.head.syn = 1
      seg.head.ack = 1

      multiplexingBuff.bufferWrite(newConn.getSrcIP, newConn.getDstIP, seg)

      // wait that state
      newConn.waitState

      (newSocket, newConn.getDstPort, newConn.getDstIP)
    } else {
      throw new SocketClosedException(socket)
    }
  }

  def virRead(socket: Int, numbytes: Int): Array[Byte] = {
    var conn: TCPConnection = null
    this.synchronized {
      if (socket < socketLeftBound || socket > socketRightBound) {
        throw new InvalidSocketException(socket)
      } else if (!socketArray.get(socket)) {
        throw new UninitialSocketException(socket)
      } else if (!boundedSocketHashMap.contains(socket)) {
        throw new UnboundSocketException(socket)
      }
      conn = boundedSocketHashMap.getOrElse(socket, null)

      if (conn.isCloseWait) {
        throw new ServerHasCloseException
      }
      
      if (conn.zombie) {
        throw new ErrorTCPStateException(conn.getState)
      }

      if (conn.blockRecv) {
        throw new ReadBlockException
      }

      if (!conn.isEstablished && !conn.isFinWait1 && !conn.isFinWait2) {
        throw new ErrorTCPStateException(conn.getState)
      }
    }

    conn.recvBuf.read(numbytes)
  }

  def virWrite(socket: Int, buf: Array[Byte]): Int = {
    var conn: TCPConnection = null
    this.synchronized {
      if (socket < socketLeftBound || socket > socketRightBound) {
        throw new InvalidSocketException(socket)
      } else if (!socketArray.get(socket)) {
        throw new UninitialSocketException(socket)
      } else if (!boundedSocketHashMap.contains(socket)) {
        throw new UnboundSocketException(socket)
      }
      conn = boundedSocketHashMap.getOrElse(socket, null)

      if (conn.zombie) {
        throw new ErrorTCPStateException(conn.getState)
      }

      if (conn.blockSend) {
        throw new WriteBlockException
      }

      if (!conn.isEstablished && !conn.isCloseWait) {
        throw new ErrorTCPStateException(conn.getState)
      }
    }
    conn.sendBuf.write(buf)
  }

  def virShutDown(socket: Int, sdType: Int) {
    sdType match {
      case 1 =>
        // write
        this.synchronized {
          if (socket < socketLeftBound || socket > socketRightBound) {
            throw new InvalidSocketException(socket)
          } else if (!socketArray.get(socket)) {
            throw new UninitialSocketException(socket)
          } else if (!boundedSocketHashMap.contains(socket)) {
            throw new UnboundSocketException(socket)
          }
          val conn = boundedSocketHashMap.getOrElse(socket, null)

          if (conn.close) {
            return
          }

          if (conn.isServerAndListen) {
            throw new ServerCannotShutdownException
          }

          if (conn.blockSend) {
            return
          }
          conn.blockSend = true
        }
        virCloseHelper(socket, false)
      case 2 =>
        // read
        this.synchronized {
          if (socket < socketLeftBound || socket > socketRightBound) {
            throw new InvalidSocketException(socket)
          } else if (!socketArray.get(socket)) {
            throw new UninitialSocketException(socket)
          } else if (!boundedSocketHashMap.contains(socket)) {
            throw new UnboundSocketException(socket)
          }
          val conn = boundedSocketHashMap.getOrElse(socket, null)

          if (conn.close) {
            return
          }

          if (conn.isServerAndListen) {
            throw new ServerCannotShutdownException
          }

          conn.blockRecv = true
        }
      case 3 =>
        // both
        this.synchronized {
          if (socket < socketLeftBound || socket > socketRightBound) {
            throw new InvalidSocketException(socket)
          } else if (!socketArray.get(socket)) {
            throw new UninitialSocketException(socket)
          } else if (!boundedSocketHashMap.contains(socket)) {
            throw new UnboundSocketException(socket)
          }
          val conn = boundedSocketHashMap.getOrElse(socket, null)

          if (conn.close) {
            return
          }

          if (conn.blockSend) {
            conn.blockRecv = true
            return
          }
          conn.blockSend = true
          conn.blockRecv = true
          virCloseHelper(socket, false)
        }
    }
  }

  def virClose(socket: Int) {
    virCloseHelper(socket, true)
  }

  def virReadAll(socket: Int, numbytes: Int): Array[Byte] = {
    var readBytes = 0
    var buf = new Array[Byte](0)
    while (readBytes < numbytes) {
      val ret = virRead(socket, numbytes - readBytes)
      readBytes += ret.length
      buf ++= ret
    }

    buf
  }

  def virWriteAll(socket: Int, buf: Array[Byte]): Int = {
    var writeBytes = 0
    while (writeBytes < buf.length) {
      val ret = virWrite(socket, buf.slice(writeBytes, buf.length))
      writeBytes += ret
    }

    writeBytes
  }

  // Below are helper functions
  def generatePortNumber(): Int = {
    var i = 0
    var result = new Random().nextInt(portRightBound + 1 - portLeftBound) + portLeftBound
    while (portArray.get(result)) {
      if (result == portRightBound) {
        result = portLeftBound
      } else {
        result += 1
      }
      i += 1
      if (i == portRightBound - portLeftBound + 1) {
        return -1
      }
    }
    result
  }

  def printSockets() {
    this.synchronized {
      val set = boundedSocketHashMap.keySet
      val list = set.toList.sortWith(_ < _)
      if (list.isEmpty) {
        println("[none]")
      } else {
        println("socket\tlocal address:port\tremote address:port\tstatus")
        for (socket <- list) {
          val conn = boundedSocketHashMap.getOrElse(socket, null)
          if (conn.getSrcIP != null) {
            println("[" + socket + "]\t[" + conn.getSrcIP.getHostAddress + ":" + conn.getSrcPort + "]\t[" + conn.getDstIP.getHostAddress + ":" + conn.getDstPort + "]\t" + conn.getState)
          } else {
            println("[" + socket + "]\t[0:0:0:0:" + conn.getSrcPort + "]\t\t[<nil>:0]\t\t" + conn.getState)
          }
        }
      }
    }
  }

  def printWindow(socket: Int) {
    this.synchronized {
      if (socket < socketLeftBound || socket > socketRightBound) {
        throw new InvalidSocketException(socket)
      } else if (!socketArray.get(socket)) {
        throw new UninitialSocketException(socket)
      } else if (!boundedSocketHashMap.contains(socket)) {
        throw new UnboundSocketException(socket)
      }
      val conn = boundedSocketHashMap.getOrElse(socket, null)

      if (conn.isServerAndListen) {
        println("Server has no window size")
      } else {
        println("(Local, Remote): " + conn.recvBuf.getAvailable + " " + conn.getFlowWindow)
      }
    }
  }

  def virCloseHelper(socket: Int, zombie: Boolean) {
    var conn: TCPConnection = null
    this.synchronized {
      if (socket < socketLeftBound || socket > socketRightBound) {
        throw new InvalidSocketException(socket)
      } else if (!socketArray.get(socket)) {
        throw new UninitialSocketException(socket)
      } else if (!boundedSocketHashMap.contains(socket)) {
        throw new UnboundSocketException(socket)
      }

      conn = boundedSocketHashMap.getOrElse(socket, null)

      if (conn.close) {
        // maybe this is only shutdown
        if (zombie) {
          portArray.set(conn.getSrcPort, false)
          socketArray.set(conn.socket, false)
          boundedSocketHashMap.remove(conn.socket)
        }
        return
      } else {
        conn.close = true
      }

      // set to zombie status
      conn.zombie = zombie

      if (zombie) {
        portArray.set(conn.getSrcPort, false)
        socketArray.set(conn.socket, false)
        boundedSocketHashMap.remove(conn.socket)
      }

      if (conn.isServerAndListen) {
        conn.setState(TCPState.CLOSE)

        conn.semaphoreQueue.release
        return
      }

      if (!conn.isEstablished && !conn.isCloseWait) {
        throw new ErrorTCPStateException(conn.getState)
      }
    }

    conn.sendBuf.waitAvailable

    if (conn.isEstablished) {
      conn.setState(TCPState.FIN_WAIT1)
    } else {
      conn.setState(TCPState.LAST_ACK)
    }

    val seg = conn.generateTCPSegment

    seg.head.fin = 1
    seg.head.ack = 1

    multiplexingBuff.bufferWrite(conn.getSrcIP, conn.getDstIP, seg)
  }

  def removeSocket(socket: Int) {
    this.synchronized {
      if (socket != -1 && socketArray.get(socket)) {
        socketArray.set(socket, false)
      }
    }
  }

  def removeFromTCP(conn: TCPConnection) {
    this.synchronized {
      // remove from hashmap
      if (clientHashMap.contains((conn.getSrcIP, conn.getSrcPort, conn.getDstIP, conn.getDstPort))) {
        clientHashMap.remove((conn.getSrcIP, conn.getSrcPort, conn.getDstIP, conn.getDstPort))
      } else {
        serverHashMap.remove(conn.getSrcPort)
      }

      // stop the thread for application
      try {
        conn.dataSendingThread.stop
      } catch {
        case e: Exception => // nothing here
      }
    }
  }
}