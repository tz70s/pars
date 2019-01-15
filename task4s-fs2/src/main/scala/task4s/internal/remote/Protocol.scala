package task4s.internal.remote

import cats.effect.Concurrent
import fs2.io.tcp.Socket
import fs2.{Chunk, Pipe, Pull, Stream}
import task4s.SerializationProvider
import task4s.internal.Assembler.Command

private[task4s] object Protocol {
  case class Header(tpe: Command)
  case class Message(header: Header, value: AnyRef)
}

private[task4s] class ProtocolParser[F[_]: Concurrent] {

  import Protocol._

  import ProtocolParser._

  private val serializer = SerializationProvider.serializer

  def parse(socket: Socket[F]): Stream[F, Message] =
    socket.reads(ChunkSize).through(ingoing)

  private def ingoing: Pipe[F, Byte, Message] = {
    def statefulPull(stream: Stream[F, Byte]): Pull[F, Message, Unit] =
      stream.pull.uncons.flatMap {
        case Some((head, tail)) =>
          serializer.deserialize[Message](head.toArray) match {
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

private[task4s] object ProtocolParser {
  val ChunkSize: Int = pureconfig.loadConfigOrThrow[Int]("task4s.internal.remote.chunk-size")

  sealed trait BatchState
  case object FlushOut extends BatchState
}
