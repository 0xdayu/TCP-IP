package util

import java.io.{ IOException, ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }

object ConvertObject {
  // helper function
  def objectToByte[T >: Null](obj: T): Array[Byte] = {
    val buffers = new ByteArrayOutputStream
    try {
      val out = new ObjectOutputStream(buffers)
      out writeObject obj
      out.close
      buffers.toByteArray
    } catch {
      case ex: IOException => println("Error: object to byte"); null
    }
  }

  def byteToObject[T >: Null](buf: Array[Byte]): T = {
    try {
      val buffers = new ByteArrayInputStream(buf)
      val in = new ObjectInputStream(buffers)
      val obj = in.readObject
      in.close
      obj.asInstanceOf[T]
    } catch {
      case ex: IOException => println("Error: object to byte"); null
    }
  }
}