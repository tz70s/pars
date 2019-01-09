package task4s.remote

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, IO, Sync}
import fs2.{Chunk, Pipe, Pull, Stream}
import fs2.io.tcp.Socket
import task4s.remote.serialize._
import task4s.remote.tcp.{SocketClientStream, SocketServerStream, TcpSocketConfig}

class Service[F[_]: Concurrent: ContextShift]()(implicit val acg: AsynchronousChannelGroup) {
  import Service._

  private def reactor(socket: Socket[F]): Stream[F, Unit] =
    for {
      message <- socket.reads(BufferSize).through(extract())
      _ <- Stream.eval {
        message.tpe match {
          case NativeMessage.SerializableStreamEvent =>
            message.value.asInstanceOf[SerializableStream[F, Unit]].ev.compile.drain
          case NativeMessage.NormalEvent => Sync[F].delay(println(s"Get message ${message.tpe}, ${message.value}"))
        }
      }
      _ <- Stream.eval(socket.endOfOutput)
    } yield ()

  def extract(): Pipe[F, Byte, NativeMessage] = {
    def statefulPipe(stream: Stream[F, Byte]): Pull[F, NativeMessage, Unit] =
      stream.pull.uncons.flatMap {
        case Some((head, tail)) =>
          val message = NativeMessage.fromBinary(head.toArray)
          Pull.output(Chunk.singleton(message)) >> statefulPipe(tail)
        case None =>
          Pull.done
      }

    source =>
      statefulPipe(source).stream
  }

  val message = NativeMessage(NativeMessage.NormalEvent, "Hello world!")

  val streamEval = NativeMessage(NativeMessage.SerializableStreamEvent, SerializableStream(Stream.eval(IO {
    println("Hello World!")
  })))

  private def remoteWriter(socket: Socket[F]): Stream[F, Unit] =
    Stream
      .chunk(Chunk.bytes(streamEval.toBinary))
      .through(socket.writes())
      .onFinalize(socket.endOfOutput)

  def bindAndHandle: Stream[F, Unit] = SocketServerStream[F](reactor)

  def remote(rmt: TcpSocketConfig, handler: Socket[F] => Stream[F, Unit] = remoteWriter): Stream[F, Unit] =
    SocketClientStream[F](rmt, handler)
}

object Service {

  val BufferSize = 1024 * 256

  sealed trait BatchState
  case object FlushOut extends BatchState

  // TODO: should extend this into resource.
  def apply[F[_]: Concurrent: ContextShift]()(implicit acg: AsynchronousChannelGroup): Stream[F, Unit] =
    new Service().bindAndHandle

  def remote[F[_]: Concurrent: ContextShift](
      rmt: TcpSocketConfig
  )(implicit acg: AsynchronousChannelGroup): Stream[F, Unit] =
    new Service().remote(rmt)

  def remote[F[_]: Concurrent: ContextShift](rmt: TcpSocketConfig, handler: Socket[F] => Stream[F, Unit])(
      implicit acg: AsynchronousChannelGroup
  ): Stream[F, Unit] = new Service().remote(rmt, handler)
}
