package task4s

import cats.effect.IO
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import task4s.internal.remote.serialization.JSerializer

import scala.reflect.ClassTag

/**
 * Exposed Serializer interface let user customized serialization,
 * and switch it by serialization provider.
 *
 * By default, the serializer is JSerializer, which implemented by Java object serialization.
 *
 */
trait Serializer {

  /**
   * Convert a value into array of bytes.
   *
   * @param value The serialization value object.
   * @return Either wrapping of serialization result.
   */
  def serialize[M: ClassTag](value: M): Either[Throwable, Array[Byte]]

  /**
   * Convert binary to object.
   *
   * Note that you should provide the class type for the underlying cast.
   * @example {{{
   * case class SomeValue(text: String)
   *
   * val serializer = SerializationProvider.serializer
   *
   * val value = SomeValue
   * val binary = serializer.serialize(SomeValue("test"))
   *
   * val after = binary.flatMap(r => serializer.deserialize(r))
   * // Right(SomeValue("test"))
   * }}}
   *
   * @param binary The deserialize binary.
   * @return Either wrapping of deserialization result.
   */
  def deserialize[M: ClassTag](binary: Array[Byte]): Either[Throwable, M]
}

object SerializationProvider {

  private implicit val log: SelfAwareStructuredLogger[IO] = Slf4jLogger.unsafeCreate[IO]

  private[task4s] val DefaultJavaSerializer = "DefaultJavaSerializer"

  /**
   * The SPI method, get the serializer from configuration.
   *
   * By default the configuration is "DefaultJavaSerializer".
   */
  def serializer: Serializer =
    try {
      val conf = pureconfig.loadConfig[String]("task4s.serializer").getOrElse(DefaultJavaSerializer)
      if (conf == DefaultJavaSerializer) new JSerializer else Class.forName(conf).asInstanceOf[Serializer]
    } catch {
      case t: ClassNotFoundException =>
        Logger[IO]
          .error(
            s"Can't find providing class, maybe you should checkout whether class path is correct or not, error: $t"
          )
          .unsafeRunSync()
        throw t

      case t: ClassCastException =>
        Logger[IO]
          .error(s"Class cast failed, the serializer should implement Serializer trait, error: $t")
          .unsafeRunSync()
        throw t
    }
}
