package driver

import ip._
import util._

object node {
  val UsageCommand = "We only accept: [h]elp, [i]nterfaces, [r]outes," +
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
    hm.registerHandler(nodeInterface.Rip, Handler.ripHandler)
    hm.registerHandler(nodeInterface.Data, Handler.forwardHandler)

    val rece = new Receiving(nodeInterface)
    val send = new Sending(nodeInterface)

    // threads
    val hmThread = new Thread(hm)
    val receThread = new Thread(rece)
    val sendThread = new Thread(send)

    hmThread.start
    receThread.start
    sendThread.start

    println("Node all set [\"[q]uit\" to exit]")

    while (true) {
      print("> ")
      val line = readLine()
      val arrSplit = line split " "
      val arr = arrSplit.filterNot(_ == "")
      if (arr.length == 0) {
        println(UsageCommand)
      } else {
        arr(0).trim match {
          case "h" | "help" => printHelp()
          case "i" | "interfaces" => nodeInterface.printInterfaces(arr)
          case "r" | "routes" => nodeInterface.printRoutes(arr)
          case "d" | "down" => nodeInterface.interfacesDown(arr)
          case "u" | "up" => nodeInterface.interfacesUp(arr)
          case "s" | "send" => nodeInterface.generateAndSendPacket(arr, line)
          case "q" | "quit" =>
            {
              nodeInterface.socket.close
              rece.cancel
              hm.cancel
              send.cancel
              println("Exit this node")
              sys.exit(0)
            }
          case _ => println(UsageCommand)
        }
      }
    }
  }

  def printHelp() {
    println("*****************************************************************************")
    println(" [h]elp\t\t\t\tHelp Printing")
    println(" [i]nterfaces\t\t\tInterface information (local and remote IP)")
    println(" [r]outes\t\t\tRouting table")
    println(" [d]own <integer>\t\tBring one interface down")
    println(" [u]p <integer>\t\t\tBring one interface up")
    println(" [s]end <vip> <proto> <string>\tSend message to virtual IP")
    println(" [q]uit\t\t\t\tQuit the node")
    println("*****************************************************************************")
  }
}