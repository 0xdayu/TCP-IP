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

  val DefaultFlowBuffSize = 4096
  val DefaultMultiplexingBuffSize = 1024 * 1024 * 1024

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
        val newCon = new TCPConnection(socket, port, DefaultFlowBuffSize, this)
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
        if (!conn.setState(TCPState.LISTEN)) {
          throw new ErrorTCPStateException
        } else {
          serverHashMap.put(conn.getSrcPort, conn)
        }
      }
    }
  }

  def virConnect(socket: Int, addr: InetAddress, port: Int) {
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
        val newCon = new TCPConnection(socket, portNumber, DefaultFlowBuffSize, this)
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
        val ip = nodeInterface.getSrcAddr(addr)
        if (ip == null) {
          throw new DestinationUnreachable(addr)
        }
        conn.setSrcIP(ip)
        conn.setDstIP(addr)
        conn.setDstPort(port)

        clientHashMap.put((conn.getSrcIP, conn.getSrcPort, conn.getDstIP, conn.getDstPort), conn)
      }

      conn.generateAndSentFirstTCPSegment

      // finish 1 of 3 3-way handshake
      conn.setState(TCPState.SYN_SENT)

      while (conn.getState != TCPState.ESTABLISHED) {
        ;
      }
      println("Established connection succesfully")
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
      // remove from queue
      conn = boundedSocketHashMap.getOrElse(socket, null)
    }
    conn.semaphoreQueue.acquire
    if (!conn.pendingQueue.isEmpty) {
      val (tuple, newConn) = conn.pendingQueue.head
      conn.pendingQueue.remove(tuple)

      // assign new socket
      val newSocket = virSocket()
      newConn.setSocket(newSocket)

      this.synchronized {
        // put into map
        boundedSocketHashMap.put(newSocket, newConn)
        clientHashMap.put(tuple, newConn)
      }

      newConn.setState(TCPState.SYN_RECV)
      val seg = newConn.generateTCPSegment

      seg.head.syn = 1
      seg.head.ack = 1

      multiplexingBuff.bufferWrite(newConn.getSrcIP, newConn.getDstIP, seg)

      (newSocket, conn.getDstPort, conn.getDstIP)
    } else {
      null
    }
  }

  def virRead(socket: Int): Array[Byte] = {
    null
  }

  def virWrite(socket: Int, buf: Array[Byte]) {

  }

  def virShutDown(socket: Int, sdType: Int) {

  }

  def virClose(socket: Int) {

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
}