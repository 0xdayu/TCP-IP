import tcp._
import tcputil._

object test {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(75); 
		val a = new CircularArray(6);System.out.println("""a  : tcputil.CircularArray = """ + $show(a ));$skip(40); 
	  val b : Array[Byte] = "abc".getBytes;System.out.println("""b  : Array[Byte] = """ + $show(b ));$skip(13); val res$0 = 
	  a.read(1);System.out.println("""res0: Array[Byte] = """ + $show(res$0));$skip(14); val res$1 = 
	  a.write(b);System.out.println("""res1: Int = """ + $show(res$1));$skip(13); val res$2 = 
	  a.read(1);System.out.println("""res2: Array[Byte] = """ + $show(res$2));$skip(13); val res$3 = 
	  a.read(1);System.out.println("""res3: Array[Byte] = """ + $show(res$3));$skip(36); val res$4 = 
	  a.write("cdefghijklmn".getBytes);System.out.println("""res4: Int = """ + $show(res$4));$skip(13); val res$5 = 
	  a.read(2);System.out.println("""res5: Array[Byte] = """ + $show(res$5))}
	  
	  
}
