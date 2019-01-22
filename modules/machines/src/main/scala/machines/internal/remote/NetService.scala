package machines.internal.remote

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift}
import fs2.{Pipe, Stream}
import fs2.concurrent.{NoneTerminatedQueue, Queue}
import fs2.io.tcp.Socket
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import machines.internal.Protocol.Protocol
import machines.internal.remote.tcp.{SocketClientStream, SocketServerStream, TcpSocketConfig}
import scala.concurrent.duration._

private[machines] class ServerImpl[F[_]: Concurrent: ContextShift](
    implicit val acg: AsynchronousChannelGroup
) {
  private val parser = new ProtocolParser[F]

  private implicit val log: SelfAwareStructuredLogger[F] = Slf4jLogger.unsafeCreate[F]

  private def reactor(logic: Pipe[F, Protocol, Protocol])(socket: Socket[F]): Stream[F, Unit] =
    socket
      .reads(NetService.SocketReadBufferSize)
      .through(parser.decoder)
      .through(logic)
      .through(parser.encoder)
      .to(socket.writes())
      .onFinalize(socket.endOfOutput)

  def bindAndHandle(address: TcpSocketConfig)(logic: Pipe[F, Protocol, Protocol]): Stream[F, Unit] =
    SocketServerStream[F](Some(address)).handle(reactor(logic))

  def bindAndHandle(logic: Pipe[F, Protocol, Protocol]): Stream[F, Unit] =
    SocketServerStream[F]().handle(reactor(logic))
}

private[machines] class ClientImpl[F[_]: Concurrent: ContextShift](implicit val acg: AsynchronousChannelGroup) {

  private val parser = new ProtocolParser[F]

  private implicit val log: SelfAwareStructuredLogger[F] = Slf4jLogger.unsafeCreate[F]

  def writeN(rmt: TcpSocketConfig, source: Stream[F, Protocol]): Stream[F, Protocol] = {
    def cycle(queue: NoneTerminatedQueue[F, Protocol]): Stream[F, Unit] =
      remote(rmt) { socket =>
        val writes = source.through(parser.encoder).to(socket.writes()).onFinalize(socket.endOfOutput)

        val reads = socket
          .reads(NetService.SocketReadBufferSize)
          .through(parser.decoder)
          .map(p => Some(p))
          .to(queue.enqueue)

        writes ++ reads ++ Stream.eval(queue.enqueue1(None))
      }

    for {
      q <- Stream.eval(Queue.boundedNoneTerminated[F, Protocol](100))
      packet <- q.dequeue concurrently cycle(q)
      _ <- Stream.eval(Logger[F].debug(s"Client get the return command $packet"))
    } yield packet
  }

  private def remote(rmt: TcpSocketConfig)(handler: Socket[F] => Stream[F, Unit]): Stream[F, Unit] =
    SocketClientStream[F].handle(rmt, handler)
}

/**
 * Intermediate class for prefix context type.
 *
 * @example {{{
 * val server = NetService[IO].bindAndHandle(assembler)
 *
 * val client = NetService[IO].writeN(remote, someStream)
 * }}}
 */
private[machines] class NetService[F[_]: Concurrent: ContextShift](implicit val acg: AsynchronousChannelGroup) {
  def bindAndHandle(logic: Pipe[F, Protocol, Protocol]): Stream[F, Unit] = new ServerImpl[F].bindAndHandle(logic)

  def bindAndHandle(address: TcpSocketConfig, logic: Pipe[F, Protocol, Protocol]): Stream[F, Unit] =
    new ServerImpl[F].bindAndHandle(address)(logic)

  def writeN(rmt: TcpSocketConfig, source: Stream[F, Protocol]): Stream[F, Protocol] =
    new ClientImpl[F].writeN(rmt, source)

  def backOffWriteN(rmt: TcpSocketConfig,
                    source: Stream[F, Protocol],
                    retry: Int = 3,
                    sleep: FiniteDuration = 100.millis,
                    factors: Int = 2): Stream[F, Protocol] =
    writeN(rmt, source).handleErrorWith { throwable =>
      if (retry > 0) backOffWriteN(rmt, source, retry - 1, sleep * factors) else Stream.raiseError(throwable)
    }
}

private[machines] object NetService {
  val SocketReadBufferSize: Int = pureconfig.loadConfigOrThrow[Int]("machines.remote.buffer-size")

  def address: TcpSocketConfig = SocketServerStream.ServerSocketConfig

  def apply[F[_]: Concurrent: ContextShift](implicit acg: AsynchronousChannelGroup) = new NetService[F]()
}
