import tcp._
import tcputil._

object test {
		val a = new CircularArray(6)      //> a  : tcputil.CircularArray = tcputil.CircularArray@73c6c3b2
	  val b : Array[Byte] = "abc".getBytes    //> b  : Array[Byte] = Array(97, 98, 99)
	  a.read(1)                               //> res0: Array[Byte] = Array()
	  a.write(b)                              //> res1: Int = 3
	  a.read(1)                               //> res2: Array[Byte] = Array(97)
	  a.read(1)                               //> res3: Array[Byte] = Array(98)
	  a.write("cdefghijklmn".getBytes)        //> res4: Int = 5
	  a.read(2)                               //> res5: Array[Byte] = Array(99, 99)
	  
	  
}