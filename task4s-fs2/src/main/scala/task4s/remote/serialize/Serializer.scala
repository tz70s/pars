package task4s.remote.serialize

import java.io._

object SerializationProvider {
  def serializer: Serializer = new JSerializer
}

trait Serializer {
  def toBinary[M](obj: M): Either[Throwable, Array[Byte]]
  def fromBinary[M](bytes: Array[Byte]): Either[Throwable, M]
}

class JSerializer extends Serializer {

  override def toBinary[M](obj: M): Either[Throwable, Array[Byte]] = {
    val array = new ByteArrayOutputStream()
    val outputStream = new ObjectOutputStream(array)

    try {
      outputStream.writeObject(obj)
      Right(array.toByteArray)
    } catch {
      case t: IOException => Left(t)
    } finally {
      outputStream.close()
    }
  }

  override def fromBinary[M](bytes: Array[Byte]): Either[Throwable, M] = {
    val array = new ByteArrayInputStream(bytes)
    val inputStream = new ObjectInputStream(array)

    try {
      val obj = inputStream.readObject()
      Right(obj.asInstanceOf[M])
    } catch {
      case t: IOException => Left(t)
    } finally {
      inputStream.close()
    }
  }
}
