package driver

import ip._
import util._

object node {
  val UsageCommand = "We only accept: [i]nterfaces, [r]outes," +
    "[d]own <integer>, [u]p <integer>, [s]end <vip> <proto> <string>, [q]uit"

  var nodeInterface: NodeInterface = _

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

    //register 200 and 0 protocol handler
    val hm = new HandlerManager(nodeInterface)
    hm.registerHandler(200, Handler.ripHandler)
    hm.registerHandler(0, Handler.forwardHandler)
    
    // threads
    (new Thread(hm)).start
    (new Thread(new Receiving(nodeInterface))).start
    (new Thread(new Sending(nodeInterface))).start
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
          case "i" | "interfaces" => nodeInterface.printInterfaces(arr)
          case "r" | "routes" => nodeInterface.printRoutes(arr)
          case "d" | "down" => nodeInterface.interfacesDown(arr)
          case "u" | "up" => nodeInterface.interfacesUp(arr)
          //case "s" | "send" => sendPacket(arr)
          case "q" | "quit" =>
            { println("Exit this node"); nodeInterface.socket.close; sys.exit(0) } 
          case _ => println(UsageCommand)
        }
      }
    }
  }

  
}