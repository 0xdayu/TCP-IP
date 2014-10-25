import  util._
import ip._

object test {
	//val a = 11
	val byte = -120                           //> byte  : Int = -120
	val b = 0                                 //> b  : Int = 0
	val result = ((byte | (b << 8)) & 0xffff) //> result  : Int = 65416
}