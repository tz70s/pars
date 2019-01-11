package task4s.remote

import java.nio.charset.StandardCharsets

import fs2.Stream
import task4s.remote.Protocol.MessageType
import task4s.remote.serialize.SerializableStreamT

/**
 * The protocol design:
 *
 * 1. The size of header.
 * 2. The size of body.
 * 3. Header options, with one word for each and collect into set.
 * 4. Header field, to be extend.
 */
object Protocol {

  sealed trait MessageType
  case object NormalEvent extends MessageType
  case object SerializableStreamEvent extends MessageType

  // Regular sizing for alignment.
  val SizeOfHeaderPlaceHolder = 8
  val SizeOfBodyPlaceHolder = 8

  // Protocol options, to be extend.
  object Options {
    val NormalEvent: Byte = 'E'
    val TaskOffload: Byte = 'T'
    val Delimiter: Byte = 'X'
  }

  object Fields {
    val ReflectClassName: Byte = 'C'

    val StringMagicSeparator = "-F-"
    val MagicSeparator: Array[Byte] = StringMagicSeparator.getBytes(StandardCharsets.UTF_8)
    val Delimiter: Array[Byte] = "-X-".getBytes(StandardCharsets.UTF_8)
  }
}

case class Header(bodySize: Int, options: Set[Byte], fields: Map[Byte, String])

case class Message(tpe: MessageType, value: AnyRef)

object Message {

  import Protocol._

  def fromStream[F[_], A](eval: Stream[F, A]) = Message(SerializableStreamEvent, SerializableStreamT(eval))
}
