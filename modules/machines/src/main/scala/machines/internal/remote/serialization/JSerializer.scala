package machines.internal.remote.serialization

import java.io._

import machines.Serializer

import scala.reflect.ClassTag

/**
 * INTERNAL API.
 *
 * Default Java Serializer.
 *
 * For performance reason, may switch to another fast serializer for system messages, i.e. Protocol Buffer.
 */
private[machines] class JSerializer extends Serializer {

  override def serialize[M: ClassTag](obj: M): Either[Throwable, Array[Byte]] = {
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

  override def deserialize[M: ClassTag](bytes: Array[Byte]): Either[Throwable, M] = {
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
