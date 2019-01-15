package task4s.remote

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift}
import fs2.Stream
import fs2.io.tcp.Socket
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import task4s.remote.tcp.{SocketClientStream, SocketServerStream, TcpSocketConfig}

class Service[F[_]: Concurrent: ContextShift](implicit val acg: AsynchronousChannelGroup) {

  import Service._

  private val parser = new ProtocolParser[F]

  private implicit val log: SelfAwareStructuredLogger[F] = Slf4jLogger.unsafeCreate[F]

  private def reactor(socket: Socket[F]): Stream[F, Unit] =
    for {
      message <- socket.reads(ChunkSize).through(parser.parse)
      _ <- Stream.eval(socket.endOfOutput)
    } yield ()

  def bindAndHandle: Stream[F, Unit] = SocketServerStream[F](reactor)

  def remote(rmt: TcpSocketConfig, handler: Socket[F] => Stream[F, Unit]): Stream[F, Unit] =
    SocketClientStream[F](rmt, handler)
}

object Service {

  val ChunkSize: Int = pureconfig.loadConfigOrThrow[Int]("task4s.remote.chunk-size")

  sealed trait BatchState
  case object FlushOut extends BatchState

  def apply[F[_]: Concurrent: ContextShift](implicit acg: AsynchronousChannelGroup): Stream[F, Unit] =
    new Service().bindAndHandle

  def remote[F[_]: Concurrent: ContextShift](rmt: TcpSocketConfig, handler: Socket[F] => Stream[F, Unit])(
      implicit acg: AsynchronousChannelGroup
  ): Stream[F, Unit] = new Service().remote(rmt, handler)
}
