package task4s.remote

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift}
import fs2.{Chunk, Pipe, Pull, Stream}
import fs2.io.tcp.Socket
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import task4s.remote.serialize._
import task4s.remote.tcp.{SocketClientStream, SocketServerStream, TcpSocketConfig}

class Service[F[_]: Concurrent: ContextShift]()(implicit val acg: AsynchronousChannelGroup) {

  import Service._

  import Protocol._

  private implicit val log = Slf4jLogger.unsafeCreate[F]

  private val serializer = SerializationProvider.serializer

  private def reactor(socket: Socket[F]): Stream[F, Unit] =
    for {
      message <- socket.reads(ChunkSize).through(extract())
      _ <- Stream.eval {
        message.tpe match {
          case SerializableStreamEvent =>
            message.value.asInstanceOf[SerializableStreamF[F]].ev.compile.drain
          case NormalEvent => Logger[F].info(s"Get message ${message.tpe}, ${message.value}")
        }
      }
      _ <- Stream.eval(socket.endOfOutput)
    } yield ()

  def extract(): Pipe[F, Byte, Message] = {
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

  def bindAndHandle: Stream[F, Unit] = SocketServerStream[F](reactor)

  def remote(rmt: TcpSocketConfig, handler: Socket[F] => Stream[F, Unit]): Stream[F, Unit] =
    SocketClientStream[F](rmt, handler)
}

object Service {

  val ChunkSize: Int = pureconfig.loadConfigOrThrow[Int]("task4s.remote.chunk-size")

  sealed trait BatchState
  case object FlushOut extends BatchState

  // TODO: should extend this into resource.
  def apply[F[_]: Concurrent: ContextShift]()(implicit acg: AsynchronousChannelGroup): Stream[F, Unit] =
    new Service().bindAndHandle

  def remote[F[_]: Concurrent: ContextShift](rmt: TcpSocketConfig, handler: Socket[F] => Stream[F, Unit])(
      implicit acg: AsynchronousChannelGroup
  ): Stream[F, Unit] = new Service().remote(rmt, handler)
}
