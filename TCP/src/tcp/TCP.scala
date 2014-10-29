package tcp

import tcputil._
import exception._
import java.net.InetAddress
import java.util.BitSet
import scala.collection.mutable.HashMap
import scala.util.Random
import scala.compat.Platform

class TCP {
  // file descriptor, 0 - input, 1 - output, 2 - error
  // start from 3 to 65535 (2^16 - 1) or less

  // TODO: need to be confirmed
  val DefaultFlowBuffSize = 4096

  val socketLeftBound = 3
  val socketRightBound = 65535

  val portLeftBound = 1024
  val portRightBound = 65535

  val socketArray = new BitSet
  val boundedSocketHashMap = new HashMap[Int, TCPConnection]

  // port number, start from 1024 to 65535 (2^16 - 1)
  val portArray = new BitSet
  val usedPortHashMap = new HashMap[Int, TCPConnection]

  def virSocket(): Int = {
    for (i <- Range(socketLeftBound, socketRightBound + 1)) {
      if (!socketArray.get(i)) {
        socketArray.set(i)
        i
      }
    }

    throw new SocketUsedUpException
  }

  
  
  def generateSequenceN

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
      portArray.set(port)
      val newCon = new TCPConnection(socket, port, DefaultFlowBuffSize)
      boundedSocketHashMap.put(socket, newCon)
      usedPortHashMap.put(port, newCon)
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
      if (!boundedSocketHashMap.getOrElse(socket, null).setState(TCPState.LISTEN, TCPState.CLOSE)) {
        throw new ErrorTCPStateException
      }
    }
  }

  def virConnect(socket: Int, addr: InetAddress, port: Int) {
    if (socket < socketLeftBound || socket > socketRightBound) {
      throw new InvalidSocketException(socket)
    } else if (!socketArray.get(socket)) {
      throw new UninitialSocketException(socket)
    } else if (!boundedSocketHashMap.contains(socket)) {
      val portNumber = generatePortNumber
      portArray.set(portNumber)
      if (portArray == -1) {
        throw new PortUsedUpException
      }
      val newCon = new TCPConnection(socket, portNumber, DefaultFlowBuffSize)
      boundedSocketHashMap.put(socket, newCon)
      usedPortHashMap.put(portNumber, newCon)
    }

    // initial all parameters for the TCP connection
    if (port < portLeftBound || port > portRightBound) {
      throw new InvalidPortException(port)
    } else {
      val conn = boundedSocketHashMap.getOrElse(socket, null)
      conn.dstIP = addr
      conn.dstPort = port
      
      //generate TCP segment
      val newTCPSegment = new TCPSegment
      val newTCPHead = new TCPHead
      // initial tcp packet
      newTCPHead.srcPort = conn.srcPort
      newTCPHead.dstPort = conn.dstPort 
      newTCPHead.seqNum = conn.seqNum
      newTCPHead.syn = 1
      newTCPHead.winSize = conn.recvBuf.getAvailable
      
      
    }
  }

  def virAccept(socket: Int): TCPConnection = {
    null
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
    if (usedPortHashMap.size == portRightBound - portLeftBound + 1) {
      -1
    } else {
      var result = new Random().nextInt(portRightBound + 1 - portLeftBound) + portLeftBound
      while (!portArray.get(result)) {
        if (result == portRightBound) {
          result = portLeftBound
        } else {
          result += 1
        }
      }
      result
    }
  }
  
}