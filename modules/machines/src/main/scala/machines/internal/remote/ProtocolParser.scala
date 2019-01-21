package machines.internal.remote

import cats.effect.Concurrent
import fs2.{Chunk, Pipe, Pull, Stream}
import machines.internal.Protocol.Protocol
import machines.SerializationProvider

private[machines] class ProtocolParser[F[_]: Concurrent] {

  private val serializer = SerializationProvider.serializer

  def decoder: Pipe[F, Byte, Protocol] = {
    def statefulPull(stream: Stream[F, Byte]): Pull[F, Protocol, Unit] =
      stream.pull.uncons.flatMap {
        case Some((head, tail)) =>
          serializer.deserialize[Protocol](head.toArray) match {
            case Right(message) => Pull.output(Chunk.singleton(message)) >> statefulPull(tail)
            case Left(cause) => Pull.raiseError(cause)
          }
        case None =>
          Pull.done
      }

    source =>
      statefulPull(source).stream
  }

  def encoder: Pipe[F, Protocol, Byte] = { packets =>
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
