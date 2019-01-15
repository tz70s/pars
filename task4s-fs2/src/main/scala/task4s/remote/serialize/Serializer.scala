package task4s.remote.serialize

import java.io._

import scala.reflect.ClassTag

object SerializationProvider {
  def serializer: Serializer = new JSerializer
}

trait Serializer {
  def toBinary[M: ClassTag](obj: M): Either[Throwable, Array[Byte]]
  def fromBinary[M: ClassTag](bytes: Array[Byte]): Either[Throwable, M]
}

/**
 * Default Java Serializer.
 *
 * For performance reason, may switch to another fast serializer for system messages, i.e. Protocol Buffer.
 *
 * INTERNAL API.
 */
private[task4s] class JSerializer extends Serializer {

  override def toBinary[M: ClassTag](obj: M): Either[Throwable, Array[Byte]] = {
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

  override def fromBinary[M: ClassTag](bytes: Array[Byte]): Either[Throwable, M] = {
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
