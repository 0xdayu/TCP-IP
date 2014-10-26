package iputil

object ConvertNumber {
	// c conversion
	def shortToUint8(num: Short): Byte = (num & 0xff).asInstanceOf[Byte]
	def intToUint16(num: Int): Short = (num & 0xffff).asInstanceOf[Short]
	def longToUint32(num: Long): Int = (num & 0xffffffff).asInstanceOf[Int]
	
	// java conversion
	def uint8ToShort(num: Byte): Short = (num & 0xff).asInstanceOf[Short]
	def uint16ToInt(num: Short): Int = (num & 0xffff).asInstanceOf[Int]
	def uint32ToLong(num: Int): Long = (num & 0xffffffff).asInstanceOf[Long]
}