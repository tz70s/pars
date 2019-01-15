package task4s.remote

import java.nio.charset.StandardCharsets

import cats.effect.Concurrent
import fs2.{Chunk, Pipe, Pull, Stream}
import task4s.Channel
import task4s.remote.Protocol.EventT
import task4s.remote.serialize.SerializationProvider

object Protocol {

  sealed trait EventT

  sealed trait ChannelEventT extends EventT

  object ChannelEventT {
    case class Send(to: Channel[_]) extends ChannelEventT
    case class Ok(to: Channel[_]) extends ChannelEventT
    case class Err(throwable: Throwable) extends ChannelEventT
  }

  sealed trait MachineSignalT extends EventT

  object MachineSignalT {

    /** Spawn is responsible for spawning machine instance via class loading. */
    case object Spawn extends MachineSignalT
    case object Down extends MachineSignalT
    case class Ok() extends MachineSignalT
    case class Err(throwable: Throwable) extends MachineSignalT
  }

  object Fields {
    val ReflectClassName: Byte = 'C'

    val StringMagicSeparator = "-F-"
    val MagicSeparator: Array[Byte] = StringMagicSeparator.getBytes(StandardCharsets.UTF_8)
    val Delimiter: Array[Byte] = "-X-".getBytes(StandardCharsets.UTF_8)
  }

}

class ProtocolParser[F[_]: Concurrent] {

  private val serializer = SerializationProvider.serializer

  def parse: Pipe[F, Byte, Message] = {
    def statefulPull(stream: Stream[F, Byte]): Pull[F, Message, Unit] =
      stream.pull.uncons.flatMap {
        case Some((head, tail)) =>
          serializer.fromBinary[Message](head.toArray) match {
            case Right(message) => Pull.output(Chunk.singleton(message)) >> statefulPull(tail)
            case Left(cause) => Pull.raiseError(cause)
          }
        case None =>
          Pull.done
      }

    source =>
      statefulPull(source).stream
  }
}

case class Header(tpe: EventT)

case class Message(header: Header, value: AnyRef)

object Message {}
