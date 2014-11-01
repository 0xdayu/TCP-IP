package tcputil

import java.text.DecimalFormat

object PrintTCPSegment {
  def printBinary(bArray: Array[Byte]) {
    var count = 0
    val numFormat = new DecimalFormat("00000000")
    for (b <- bArray) {
      if (count == 4) {
        count = 0
        println
      }
      print(numFormat.format((Integer.valueOf(Integer.toBinaryString(b & 0xff)))) + "  |  ")
      count += 1
    }
    println
    println
  }
}