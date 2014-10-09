import  util._
import ip._

object test {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(72); 
	//val a = 11
	val byte = -120;System.out.println("""byte  : Int = """ + $show(byte ));$skip(11); 
	val b = 0;System.out.println("""b  : Int = """ + $show(b ));$skip(43); 
	val result = ((byte | (b << 8)) & 0xffff);System.out.println("""result  : Int = """ + $show(result ))}
}
