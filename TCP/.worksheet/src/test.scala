import tcp._
import tcputil._

object test {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(62); 

  var a: Int = 1;System.out.println("""a  : Int = """ + $show(a ));$skip(64); 
  def x(): Int = {
    this.synchronized {
      y(1)
    }
  };System.out.println("""x: ()Int""");$skip(36); 

  def y(c: Int): Int = {
    c
  };System.out.println("""y: (c: Int)Int""");$skip(53); 
  def main(args: Array[String]) {
    println(a)
  };System.out.println("""main: (args: Array[String])Unit""")}
}
