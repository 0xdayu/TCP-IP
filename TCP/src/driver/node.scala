package driver

import ip._
import util._
import java.net.InetAddress
import tcp._
import scala.io._
import java.io._

object node {
  val UsageCommand = "We only accept: [h]help, [li]interfaces, [lr]routes, " +
    "[d]down <integer>, [u]up <integer>, [a]accept <port>, [c]connect <ip> <port>, [si]sendip <ip> <proto> <data>, " +
    "[s/w]send <socket> <data>, [r]recv <socket> <numbytes> <y/n>, [sf]sendfile <filename> <ip> <port>, " +
    "[rf]recvfile <filename> <port>, [wd]window <socket>, [sd]shutdown <socket> <read/write/both>, " +
    "[cl]close <socket>, [m]mtu <integer0> <integer1>, [q]quit"

  var nodeInterface: NodeInterface = _

  var tcp: TCP = _

  /**
   * 1. Input thread (main)
   * 2. HandlerManager thread
   * 3. Receiving thread
   * 4. Sending thread
   */
  def main(args: Array[String]) {
    if (args.length != 1) {
      println("Usage: node <linkfile>")
      sys.exit(1)
    }

    nodeInterface = new NodeInterface
    nodeInterface.initSocketAndInterfaces(args(0))

    // TCP
    tcp = new TCP(nodeInterface)
    val multiplexing = new Multiplexing(nodeInterface, tcp)
    val demultiplexing = new Demultiplexing(tcp)

    val multiThread = new Thread(multiplexing)
    val demultiThread = new Thread(demultiplexing)

    multiThread.start
    demultiThread.start

    //register 200 and 0 protocol handler
    val hm = new HandlerManager(nodeInterface, tcp)
    hm.registerHandler(nodeInterface.Rip, Handler.ripHandler)
    hm.registerHandler(nodeInterface.Data, Handler.forwardHandler)
    hm.registerHandler(nodeInterface.TCP, Handler.tcpHandler)

    // IP
    val rece = new Receiving(nodeInterface)
    val send = new Sending(nodeInterface)

    // threads
    val hmThread = new Thread(hm)
    val receThread = new Thread(rece)
    val sendThread = new Thread(send)

    hmThread.start
    receThread.start
    sendThread.start

    println("Node all set [\"[q]quit\" to exit]")

    while (true) {
      print("> ")
      val line = readLine()
      val arrSplit = line split " "
      val arr = arrSplit.filterNot(_ == "")
      if (arr.length == 0) {
        println(UsageCommand)
      } else {
        arr(0).trim match {
          case "h" | "help" => helpCmd()
          case "li" | "interfaces" => interfacesCmd(arr)
          case "lr" | "routes" => routesCmd(arr)
          case "ls" | "sockets" => socketsCmd(arr)
          case "d" | "down" => downCmd(arr)
          case "u" | "up" => upCmd(arr)
          case "a" | "accept" => acceptCmd(arr)
          case "c" | "connect" => connectCmd(arr)
          case "si" | "sendip" => sendipCmd(arr, line)
          case "s" | "w" | "send" => sendCmd(arr, line)
          case "r" | "recv" => recvCmd(arr)
          case "sf" | "sendfile" => sendFileCmd(arr)
          case "rf" | "recvfile" => recvFileCmd(arr)
          case "wd" | "window" => windowCmd(arr)
          case "sd" | "shutdown" => shutDownCmd(arr)
          case "cl" | "close" => closeCmd(arr)
          case "m" | "mtu" => mtuCmd(arr)
          case "q" | "quit" => {
            if (arr.length != 1) {
              println(UsageCommand)
            } else {
              nodeInterface.expire.cancel
              nodeInterface.periodicUpdate.cancel
              nodeInterface.socket.close
              rece.cancel
              hm.cancel
              send.cancel
              multiplexing.cancel
              demultiplexing.cancel
              println("Exit this node")
              sys.exit(0)
            }
          }
          case _ => println(UsageCommand)
        }
      }
    }
  }

  def helpCmd() {
    println("- [h]help: Print this list of commands.")
    println("- [li]interfaces: Print information about each interface, one per line.")
    println("- [lr]routes: Print information about the route to each known destination, one per line.")
    println("- [ls]sockets: List all sockets, along with the state the TCP connection associated with them is in, and their current window sizes.")
    println("- [d]down <integer>: Bring an interface \"down\".")
    println("- [u]up <integer>: Bring an interface \"up\" (it must be an existing interface, probably one you brought down).")
    println("- [a]accept <port>: : Spawn a socket, bind it to the given port, and start accepting connections on that port.")
    println("- [c]connect <ip> <port>: Attempt to connect to the given ip address, in dot notation, on the given port.")
    println("- [si]sendip <ip> <proto> <data>: Send a string on ip and proto")
    println("- [s/w]send <socket> <data>: Send a string on a socket.")
    println("- [r]recv <socket> <numbytes> <y/n>: Try to read data from a given socket. If the last argument is y, then you should block until numbytes is received, or the connection closes. If n, then don't block; return whatever recv returns. Default is n.")
    println("- [sf]sendfile <filename> <ip> <port>: Connect to the given ip and port, send the entirety of the specified file, and close the connection.")
    println("- [rf]recvfile <filename> <port>: Listen for a connection on the given port. Once established, write everything you can read from the socket to the given file. Once the other side closes the connection, close the connection as well.")
    println("- [wd]window <socket>: Print the socket's send/receive window size.")
    println("- [sd]shutdown <socket> <read/write/both>: virShutdown on the given socket. If read is given, close only the reading side. If write is given, close only the writing side. If both is given, close both sides. Default is write.")
    println("- [cl]close <socket>: virClose on the given socket.")
    println("- [m]mtu <integer0> <integer1>: Set the MTU for link integer0 to integer1 bytes.")
    println("- [q]quit: Quit the node.")
  }

  def interfacesCmd(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      nodeInterface.printInterfaces
    }
  }

  def routesCmd(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      nodeInterface.printRoutes
    }
  }

  def socketsCmd(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      tcp.printSockets
    }
  }

  def downCmd(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      val num = arr(1).trim.toInt
      nodeInterface.interfacesDown(num)
    } else {
      println("[d]down <integer>: input should be number: " + arr(1).trim)
    }
  }

  def upCmd(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      val num = arr(1).trim.toInt
      nodeInterface.interfacesUp(num)
    } else {
      println("[u]up <integer>: input should be number: " + arr(1).trim)
    }
  }

  def acceptCmd(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      val port = arr(1).trim.toInt
      try {
        val vsocket = tcp.virSocket
        tcp.virBind(vsocket, null, port)
        tcp.virListen(vsocket)

        new Thread(new TCPAccept(tcp, vsocket)).start
      } catch {
        case e: Exception => println(e.getMessage)
      }
    } else {
      println("[a]accept <port>: input should be port number: " + arr(1).trim)
    }
  }

  def connectCmd(arr: Array[String]) {
    if (arr.length != 3) {
      println(UsageCommand)
    } else if (arr(2).trim.forall(_.isDigit)) {
      try {
        val strIp = arr(1)
        var ip = InetAddress.getByName(strIp)
        val port = arr(2).trim.toInt
        try {
          val vsocket = tcp.virSocket
          tcp.virConnect(vsocket, ip, port)
        } catch {
          case e: Exception => println(e.getMessage)
        }
      } catch {
        case _: Throwable =>
          println("Invalid IP Address")
      }
    } else {
      println("[c]connect <ip> <port>: input should be port number: " + arr(2).trim)
    }
  }

  def sendipCmd(arr: Array[String], line: String) {
    if (arr.length <= 3) {
      println(UsageCommand)
    } else if (arr(2).trim.forall(_.isDigit)) {
      try {
        val dstVirtIp = arr(1)
        val proto = arr(2).toInt
        val len = line.indexOf(arr(2), line.indexOf(arr(1)) + arr(1).length) + 1 + arr(2).length
        val data = line.getBytes().slice(len, line.length)
        val ip = InetAddress.getByName(dstVirtIp)
        nodeInterface.generateAndSendPacket(ip, proto, data)
      } catch {
        case _: Throwable =>
          println("Invalid IP Address")
      }
    } else {
      println("[si]sendip <ip> <proto> <data>: input should be proto number: " + arr(2).trim)
    }
  }

  def sendCmd(arr: Array[String], line: String) {
    if (arr.length <= 2) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      val socket = arr(1).toInt
      val len = line.indexOf(arr(1)) + arr(1).length + 1
      val data = line.getBytes().slice(len, line.length)
      try {
        tcp.virWrite(socket, data)
      } catch {
        case e: Exception => println(e.getMessage)
      }
    } else {
      println("[s/w]send <socket> <data>: input should be socket number: " + arr(1).trim)
    }
  }

  def recvCmd(arr: Array[String]) {
    if (arr.length != 4) {
      println(UsageCommand)
    } else if (arr(3) == "y" || arr(3) == "n") {
      if (arr(1).trim.forall(_.isDigit) && arr(2).trim.forall(_.isDigit)) {
        val socket = arr(1).toInt
        val numbytes = arr(2).toInt
        val shouldLoop = arr(3)
        try {
          var buf: Array[Byte] = null
          if (shouldLoop == "y") {
            buf = tcp.virReadAll(socket, numbytes)
          } else {
            buf = tcp.virRead(socket, numbytes)
          }
          println("Recv on " + numbytes + " bytes returned " + buf.length + "; contents of buffer: " + new String(buf.map(_.toChar)))
        } catch {
          case e: Exception => println(e.getMessage)
        }
      } else {
        println("[r]recv <socket> <numbytes> <y/n>: input should be socket number: " + arr(2).trim + " and numbytes: " + arr(3).trim)
      }
    } else {
      println("[r]recv <socket> <numbytes> <y/n>: input should be y/n: " + arr(3).trim)
    }
  }

  def sendFileCmd(arr: Array[String]) {
    if (arr.length != 4) {
      println(UsageCommand)
    } else if (arr(3).trim.forall(_.isDigit)) {
      try {
        val filename = arr(1)
        val strIp = arr(2)
        var ip = InetAddress.getByName(strIp)
        val port = arr(3).trim.toInt
        try {
          val socket = tcp.virSocket
          tcp.virConnect(socket, ip, port)

          var source: BufferedSource = null
          try {
            source = Source.fromFile(filename)
          } catch {
            case _: Throwable => println("The wrong path of file: " + filename)
          }

          if (source != null) {
            new Thread(new SendFile(socket, source, tcp)).start
          }
        } catch {
          case e: Exception => println(e.getMessage)
        }
      } catch {
        case _: Throwable =>
          println("Invalid IP Address")
      }
    } else {
      println("[sf]sendfile <filename> <ip> <port>: input should be port number: " + arr(3).trim)
    }
  }

  def recvFileCmd(arr: Array[String]) {
    if (arr.length != 3) {
      println(UsageCommand)
    } else if (arr(2).trim.forall(_.isDigit)) {
      val filename = arr(1)
      val port = arr(2).toInt

      try {
        val socket = tcp.virSocket
        tcp.virBind(socket, null, port)

        tcp.virListen(socket)

        var source: PrintWriter = null
        try {
          source = new PrintWriter(new File("test.txt"))
        } catch {
          case _: Throwable => println("The wrong path of file: " + filename)
        }

        if (source != null) {
          new Thread(new RecvFile(socket, source, tcp)).start
        }
      } catch {
        case e: Exception => println(e.getMessage)
      }
    } else {
      println("[rf]sendfile <filename> <port>: input should be port number: " + arr(2).trim)
    }
  }

  def windowCmd(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      val socket = arr(1).trim.toInt
      // TODO
    } else {
      println("[wd]window <socket>: input should be socket number: " + arr(1).trim)
    }
  }

  def shutDownCmd(arr: Array[String]) {
    if (arr.length != 3) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      if (arr(2) == "read" || arr(2) == "write" || arr(2) == "both") {
        val socket = arr(1).toInt
        var sdType: Int = 1
        if (arr(2) == "write") {
          sdType = 1
        } else if (arr(2) == "read") {
          sdType = 2
        } else {
          sdType = 3
        }
        try {
          tcp.virShutDown(socket, sdType)
        } catch {
          case e: Exception => println(e.getMessage)
        }
      } else {
        println("[sd]shutdown <socket> <read/write/both>: input should be read/write/both: " + arr(2))
      }
    } else {
      println("[sd]shutdown <socket> <read/write/both>: input should be socket number: " + arr(1).trim)
    }
  }

  def closeCmd(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit)) {
      val socket = arr(1).trim.toInt
      try {
        tcp.virClose(socket)
      } catch {
        case e: Exception => println(e.getMessage)
      }
    } else {
      println("[cl]close <socket>: input should be socket number: " + arr(1).trim)
    }
  }

  def mtuCmd(arr: Array[String]) {
    if (arr.length != 3) {
      println(UsageCommand)
    } else if (arr(1).trim.forall(_.isDigit) && arr(2).trim.forall(_.isDigit)) {
      val num = arr(1).trim.toInt
      val mtu = arr(2).trim.toInt
      nodeInterface.setMTU(num, mtu)
    } else {
      println("[m]tu: input should be two numbers")
    }
  }
}