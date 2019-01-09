package task4s.remote.serialize

import java.io._

import com.typesafe.scalalogging.Logger

object Serializer {

  private val log = Logger(this.getClass)

  def toBinary[M <: Serializable](obj: M): Array[Byte] = {
    val array = new ByteArrayOutputStream()
    val outputStream = new ObjectOutputStream(array)
    try {
      outputStream.writeObject(obj)
      outputStream.flush()
      outputStream.close()
    } catch {
      case t: IOException =>
        log.error(s"Serialization error: ${t.getMessage}")
        throw t
    }
    array.toByteArray
  }

  def fromBinary[M <: Serializable](bytes: Array[Byte]): M = {
    val array = new ByteArrayInputStream(bytes)
    val inputStream = new ObjectInputStream(array)

    try {
      val obj = inputStream.readObject()
      inputStream.close()
      obj.asInstanceOf[M]
    } catch {
      case t: IOException =>
        log.error(s"Serialization error: ${t.getMessage}")
        throw t
    }
  }
}
