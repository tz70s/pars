package task4s.internal.remote

import cats.effect.Concurrent
import fs2.io.tcp.Socket
import fs2.{Chunk, Pipe, Pull, Stream}
import task4s.SerializationProvider
import task4s.internal.Assembler.Packet

private[task4s] object Protocol {
  case class Header(tpe: Message)
  case class Message(header: Header, value: AnyRef)
}

private[task4s] class ProtocolParser[F[_]: Concurrent] {

  import ProtocolParser._

  private val serializer = SerializationProvider.serializer

  def parse(socket: Socket[F]): Stream[F, Packet] =
    socket.reads(ChunkSize).through(ingoing)

  private def ingoing: Pipe[F, Byte, Packet] = {
    def statefulPull(stream: Stream[F, Byte]): Pull[F, Packet, Unit] =
      stream.pull.uncons.flatMap {
        case Some((head, tail)) =>
          serializer.deserialize[Packet](head.toArray) match {
            case Right(message) => Pull.output(Chunk.singleton(message)) >> statefulPull(tail)
            case Left(cause) => Pull.raiseError(cause)
          }
        case None =>
          Pull.done
      }

    source =>
      statefulPull(source).stream
  }

  def unparse(socket: Socket[F], packets: Stream[F, Packet]): Stream[F, Unit] = {
    val chunks = for {
      packet <- packets
      binary <- Stream.emit(serializer.serialize(packet))
      chunk <- binary match {
        case Right(arr) => Stream.chunk(Chunk.bytes(arr)); case Left(err) => Stream.raiseError(err)
      }
    } yield chunk

    chunks.through(socket.writes())
  }
}

private[task4s] object ProtocolParser {
  val ChunkSize: Int = pureconfig.loadConfigOrThrow[Int]("task4s.remote.chunk-size")

  sealed trait BatchState
  case object FlushOut extends BatchState
}
