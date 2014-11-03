import tcp._
import tcputil._

object test {

  var a: Int = 1                                  //> a  : Int = 1
  def x(): Int = {
    this.synchronized {
      y(1)
    }
  }                                               //> x: ()Int

  def y(c: Int): Int = {
    c
  }                                               //> y: (c: Int)Int
  def main(args: Array[String]) {
    println(a)
  }                                               //> main: (args: Array[String])Unit
}