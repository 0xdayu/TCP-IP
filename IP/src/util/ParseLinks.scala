package util

import scala.io.Source
import java.io.{FileNotFoundException, IOException}
import java.net.InetAddress
import scala.collection.mutable.MutableList

object ParseLinks {
	def parseLinks(fileName: String): MutableList[Link] = {
	  var list = new MutableList[Link];
	  try {
	    for (line <- Source.fromFile(fileName).getLines()) {
	      val ret = parseLine(line)
	      if (ret == null) {
	        return list
	      }
	      list += ret
	    }
	    list
	  } catch {
	    case ex: FileNotFoundException => {
	    	println("Not find the file: " + fileName)
	      	sys.exit(1)
	    }
	    case ex: IOException => {
	    	println("Had an IOException trying to read that file: " + fileName)
	    	sys.exit(1)
	    }
	  }
	}
	
	def parseLine(line: String): Link = {
	  val link = new Link
	  val arr = line split ' '
	  if (arr.length != 4) {
	    return null
	  }	  
	  
	  // local interface address
	  val localArr = arr(0) split ':'
	  link.localPhysHost = InetAddress.getByName(localArr(0))
	  
	  val localPort = localArr(1).toInt
	  if (localPort < 0x0000 || localPort > 0xffff) {
	    return null
	  }
	  link.localPhysPort = localPort
	  
	  link.localVirtIP = InetAddress.getByName(arr(1))
	  
	  // next interface address
	  val remoteArr = arr(2) split ':'
	  link.remotePhysHost = InetAddress.getByName(localArr(0))
	  
	  val remotePort = localArr(1).toInt
	  if (remotePort < 0x0000 || remotePort > 0xffff) {
	    return null
	  }	  
	  link.remotePhysPort = remotePort
	  
	  link.remoteVirtIP = InetAddress.getByName(arr(3))
	  
	  link
	}
}