package util

import scala.io.Source
import java.io.{ FileNotFoundException, IOException }
import java.net.InetAddress
import scala.collection.mutable.MutableList

object ParseLinks {
  def parseLinks(fileName: String): Lnx = {
    try {
      var count = 0
      var lnx = new Lnx
      val list = Array.ofDim[Link](Source.fromFile(fileName).getLines().length - 1)
      for (line <- Source.fromFile(fileName).getLines()) {
        if (count == 0) {
          if (!parseFirst(line, lnx)) {
            println("Error: parseFirst")
            sys.exit(1)
          }
        } else {
          val ret = parseOthers(line)
          if (ret == null) {
            println("Error: parseOthers")
            sys.exit(1)
          }

          list(count - 1) = ret
        }
        count += 1
      }
      lnx.links = list
      lnx
    } catch {
      case ex: FileNotFoundException => {
        println("Not find the file: " + fileName)
        sys.exit(1)
      }
      case ex: IOException => {
        println("Had an IOException trying to read that file: " + fileName)
        sys.exit(1)
      }
      case _: Throwable => {
        println("Some other error happens")
        sys.exit(1)
      }
    }
  }

  def parseFirst(line: String, lnx: Lnx): Boolean = {
    val arr = line split ':'
    if (arr.length != 2) {
      return false
    }

    lnx.localPhysHost = InetAddress.getByName(arr(0))

    lnx.localPhysPort = arr(1).trim.toInt
    if (lnx.localPhysPort < 0x0000 || lnx.localPhysPort > 0xffff) {
      return false
    }

    true
  }

  def parseOthers(line: String): Link = {
    val link = new Link
    val arr = line split ' '
    if (arr.length != 3) {
      return null
    }

    // next interface address
    val remoteArr = arr(0) split ':'
    if (remoteArr.length != 2) {
      return null
    }

    link.remotePhysHost = InetAddress.getByName(remoteArr(0))

    link.remotePhysPort = remoteArr(1).toInt
    if (link.remotePhysPort < 0x0000 || link.remotePhysPort > 0xffff) {
      return null
    }

    // local virtual IP
    link.localVirtIP = InetAddress.getByName(arr(1))

    // remote virtual IP
    link.remoteVirtIP = InetAddress.getByName(arr(2))

    link
  }
}