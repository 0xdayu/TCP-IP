package tcp

import tcputil._
import exception._
import java.net.InetAddress
import java.util.BitSet
import scala.collection.mutable.HashMap
import scala.util.Random
import scala.compat.Platform

class TCP(nodeInterface: ip.NodeInterface) {
  // file descriptor, 0 - input, 1 - output, 2 - error
  // start from 3 to 65535 (2^16 - 1) or less

  // TODO: need to be confirmed
  val DefaultFlowBuffSize = 4096
  val DefaultMultiplexingBuffSize = 1024 * 1024 * 1024

  val socketLeftBound = 3
  val socketRightBound = 65535

  val portLeftBound = 1024
  val portRightBound = 65535

  // one socket to one connection
  val socketArray = new BitSet
  val boundedSocketHashMap = new HashMap[Int, TCPConnection]

  // port number, start from 1024 to 65535 (2^16 - 1)
  val portArray = new BitSet

  // port -> connection
  // (srcip, srcport, dstip, dstport) -> connection
  val serverHashMap = new HashMap[Int, TCPConnection]
  val clientHashMap = new HashMap[(InetAddress, Int, InetAddress, Int), TCPConnection]

  val multiplexingBuff = new FIFOBuffer(DefaultMultiplexingBuffSize)
  val demultiplexingBuff = new FIFOBuffer(DefaultMultiplexingBuffSize)

  def virSocket(): Int = {
    for (i <- Range(socketLeftBound, socketRightBound + 1)) {
      if (!socketArray.get(i)) {
        socketArray.set(i)
        return i
      }
    }

    throw new SocketUsedUpException
  }

  def virBind(socket: Int, addr: InetAddress, port: Int) {
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

  def virListen(socket: Int) {
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
        serverHashMap.put(conn.srcPort, conn)
      }
    }
  }

  def virConnect(socket: Int, addr: InetAddress, port: Int) {
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

    // initial all parameters for the TCP connection
    if (port < portLeftBound || port > portRightBound) {
      throw new InvalidPortException(port)
    } else {
      // 1 of 3 3-way handshake
      val conn = boundedSocketHashMap.getOrElse(socket, null)
      val ip = nodeInterface.getSrcAddr(addr)
      if (ip == null) {
        throw new DestinationUnreachable(addr)
      }
      conn.srcIP = ip
      conn.dstIP = addr
      conn.dstPort = port

      clientHashMap.put((conn.srcIP, conn.srcPort, conn.dstIP, conn.dstPort), conn)

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
    if (socket < socketLeftBound || socket > socketRightBound) {
      throw new InvalidSocketException(socket)
    } else if (!socketArray.get(socket)) {
      throw new UninitialSocketException(socket)
    } else if (!boundedSocketHashMap.contains(socket)) {
      throw new UnboundSocketException(socket)
    } else {
      // remove from queue
      val conn = boundedSocketHashMap.getOrElse(socket, null)
      if (!conn.pendingQueue.isEmpty) {
        val (tuple, newConn) = conn.pendingQueue.head
        conn.pendingQueue.remove(tuple)

        // assign new socket
        val newSocket = virSocket()
        newConn.socket = newSocket

        // put into map
        boundedSocketHashMap.put(newSocket, newConn)
        clientHashMap.put(tuple, newConn)

        newConn.setState(TCPState.SYN_RECV)
        val seg = newConn.generateTCPSegment

        seg.head.syn = 1
        seg.head.ack = 1
        multiplexingBuff.bufferWrite(newConn.srcIP, newConn.dstIP, seg)

        (newSocket, conn.dstPort, conn.dstIP)
      } else {
        null
      }
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
    for (socket <- boundedSocketHashMap.keySet) {
      println("Socket: " + socket + " Port: " + boundedSocketHashMap.getOrElse(socket, null).srcPort + " State: " + boundedSocketHashMap.getOrElse(socket, null).getState)
    }
  }
}