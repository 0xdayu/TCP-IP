package tcp

import exception._
import java.net.InetAddress
import java.util.BitSet
import scala.collection.mutable.HashMap

class TCP {
  // file descriptor, 0 - input, 1 - output, 2 - error
  // start from 3 to 65535 (2^16 - 1) or less

  // TODO: need to be confirmed
  val DefaultFlowBuffSize = 4096
  val DefaultSlidingWindowSize = 4096

  val socketArray = new BitSet
  val boundedSocketHashMap = new HashMap[Int, TCPConnection]

  // port number, start from 1024 to 65535 (2^16 - 1)
  val portArray = new BitSet

  def virSocket(): Int = {
    for (i <- Range(3, 65536)) {
      if (!socketArray.get(i)) {
        socketArray.set(i)
        i
      }
    }

    throw new SocketUsedUpException
  }

  def virBind(socket: Int, addr: InetAddress, port: Int) {
    // addr is not used in virBind
    // check whether socket has been asigned
    if (socket <= 3 || socket >= 65536) {
      throw new InvalidSocketException(socket)
    } else if (!socketArray.get(socket)) {
      throw new UninitialSocketException(socket)
    } else if (boundedSocketHashMap.contains(socket)) {
      throw new BoundedSocketException(socket)
    } else if (port <= 1023 || port >= 65536) {
      throw new InvalidPortException(port)
    } else if (portArray.get(port)) {
      throw new UsedPortException(port)
    } else {
      portArray.set(port)
      boundedSocketHashMap.put(socket, new TCPConnection(port, DefaultFlowBuffSize, DefaultSlidingWindowSize))
    }
  }

  def virListen(socket: Int) {

  }

  def virConnect(socket: Int, addr: InetAddress, port: Int) {

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
}