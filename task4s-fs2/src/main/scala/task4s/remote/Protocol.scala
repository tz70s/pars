package task4s.remote

import java.nio.charset.StandardCharsets

import cats.effect.Sync
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import task4s.ChannelRef
import task4s.remote.Protocol.EventT
import task4s.remote.serialize.{SerializableStreamF, SerializableStreamT}

/**
 * The protocol design:
 *
 * 1. The size of header.
 * 2. The size of body.
 * 3. Header options, with one word for each and collect into set.
 * 4. Header field, to be extend.
 */
object Protocol {

  sealed trait EventT

  sealed trait ChannelEventT extends EventT

  object ChannelEventT {
    case class Send(to: ChannelRef) extends ChannelEventT
    case class Ok(to: ChannelRef) extends ChannelEventT
    case class Err(throwable: Throwable) extends ChannelEventT
  }

  sealed trait TaskEventT extends EventT

  object TaskEventT {
    case object Create extends TaskEventT
    case object Delete extends TaskEventT
    case class Ok() extends TaskEventT
    case class Err(throwable: Throwable) extends TaskEventT
  }

  object Fields {
    val ReflectClassName: Byte = 'C'

    val StringMagicSeparator = "-F-"
    val MagicSeparator: Array[Byte] = StringMagicSeparator.getBytes(StandardCharsets.UTF_8)
    val Delimiter: Array[Byte] = "-X-".getBytes(StandardCharsets.UTF_8)
  }

  def parse[F[_]: Sync](message: Message)(implicit log: Logger[F]): Stream[F, Unit] =
    message.header.tpe match {
      case t: TaskEventT =>
        Stream.eval(message.value.asInstanceOf[SerializableStreamF[F]].ev.compile.drain)
      case c: ChannelEventT =>
        Stream.eval(Logger[F].info(s"Get message ${message.header.tpe}, ${message.value}"))
    }
}

case class Header(tpe: EventT)

case class Message(header: Header, value: AnyRef)

object Message {

  import Protocol._

  def fromStream[F[_], A](eval: Stream[F, A]) =
    Message(Header(TaskEventT.Create), SerializableStreamT(eval))
}
