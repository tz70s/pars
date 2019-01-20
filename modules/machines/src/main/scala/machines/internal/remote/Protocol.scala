package machines.internal.remote

import cats.effect.Concurrent
import fs2.{Chunk, Pipe, Pull, Stream}
import machines.SerializationProvider
import machines.internal.UnsafeFacade.Packet

private[machines] object Protocol {
  case class Header(tpe: Message)
  case class Message(header: Header, value: AnyRef)
}

private[machines] class ProtocolParser[F[_]: Concurrent] {

  private val serializer = SerializationProvider.serializer

  def bufferToPacket: Pipe[F, Byte, Packet] = {
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

  def packetToBuffer: Pipe[F, Packet, Byte] = { packets =>
    val chunks = for {
      packet <- packets
      binary <- Stream.emit(serializer.serialize(packet))
      chunk <- binary match {
        case Right(arr) => Stream.chunk(Chunk.bytes(arr)); case Left(err) => Stream.raiseError(err)
      }
    } yield chunk
    chunks
  }
}

private[machines] object ProtocolParser {
  sealed trait BatchState
  case object FlushOut extends BatchState
}
