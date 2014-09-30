package driver

import ip._
import util._
import scala.collection.mutable.{ ArrayBuffer, HashMap }
import java.net.{ InetAddress, DatagramSocket }

object node {
  val MaxReadBuf: Int = 64 * 1024
  val UsageCommand = "We only accept: [i]nterfaces, [r]outes," +
    "[d]own <integer>, [u]p <integer>, [s]end <vip> <proto> <string>, [q]uit"
  var socket: DatagramSocket = _
  val interfaceArray = new ArrayBuffer[LinkInterface]
  // dst addr, cost, next addr
  val routingTable = new HashMap[InetAddress, (Int, InetAddress)]
  val readBuf = new Array[Byte](MaxReadBuf)

  /**
   * 1. Input thread (main)
   * 2. Receiving thread
   * 3. Routing thread
   * 4. Forwarding thread
   * 5. Sending thread
   */
  def main(args: Array[String]) {
    if (args.length != 1) {
      println("Usage: node <linkfile>")
      sys.exit(1)
    }

    // create interfaces and routing table
    var id = 0
    val lnx = ParseLinks.parseLinks(args(0))
    socket = new DatagramSocket(lnx.localPhysPort, lnx.localPhysHost)

    for (link <- lnx.links) {
      val interface = new LinkInterface(link, id, socket)
      interfaceArray += interface

      routingTable.put(link.localVirtIP, (16, link.remoteVirtIP))
      id += 1
    }

    // threads
    (new Thread(new Receiving(interfaceArray, readBuf))).start
    /*
		(new Thread(new Routing(interfaceList))).start
		(new Thread(new Forwarding(interfaceList))).start
		(new Thread(new Sending(interfaceList))).start
		*/

    println("Node all set [\"[q]uit\" to exit]")

    while (true) {
      print(">")
      val line = readLine()
      val arr = line split " "
      if (arr.length == 0) {
        println(UsageCommand)
      } else {
        arr(0).trim match {
          case "i" | "interfaces" => printInterfaces(arr)
          case "r" | "routes" => printRoutes(arr)
          case "d" | "down" => interfacesDown(arr)
          case "u" | "up" => interfacesUp(arr)
          //case "s" | "send" => sendPacket(arr)
          case "q" | "quit" =>
            println("Exit this node"); sys.exit(0)
          case _ => println(UsageCommand)
        }
      }
    }
  }

  def printInterfaces(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      println("Interfaces:")
      var i = 0;
      for (interface <- interfaceArray) {
        interface.linkInterfacePrint
      }
    }
  }

  def printRoutes(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      println("Routing table:")
      for (entry <- routingTable) {
        var throughAddr: String = ""
        if (entry._1.getHostAddress() == entry._2._2.getHostAddress()) {
          throughAddr = "self"
        } else {
          throughAddr = entry._2._2.getHostAddress()
        }

        println("Route to " + entry._1.getHostAddress() + " with cost " + entry._2._1 +
          ", through " + throughAddr)
      }
    }
  }

  def interfacesDown(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).forall(_.isDigit)) {
      val num = arr(1).toInt

      if (num < interfaceArray.length) {
        interfaceArray(num).bringDown;
      } else {
        println("No such interface")
      }
    } else {
      println("input should be number")
    }
  }

  def interfacesUp(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).forall(_.isDigit)) {
      val num = arr(1).toInt

      if (num < interfaceArray.length) {
        interfaceArray(num).bringUp
      } else {
        println("No such interface")
      }
    } else {
      println("input should be number")
    }
  }
}