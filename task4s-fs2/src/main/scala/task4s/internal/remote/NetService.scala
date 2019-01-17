package task4s.internal.remote

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift}
import fs2.Stream
import fs2.concurrent.{Queue, SignallingRef}
import fs2.io.tcp.Socket
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import task4s.internal.Assembler
import task4s.internal.Assembler.Packet
import task4s.internal.remote.tcp.{SocketClientStream, SocketServerStream, TcpSocketConfig}

private[task4s] class ServerImpl[F[_]: Concurrent: ContextShift](private val assembler: Assembler[F])(
    implicit val acg: AsynchronousChannelGroup
) {
  private val parser = new ProtocolParser[F]

  private implicit val log: SelfAwareStructuredLogger[F] = Slf4jLogger.unsafeCreate[F]

  private def reactor(socket: Socket[F]): Stream[F, Unit] = {
    val retValues = for {
      packet <- socket.reads(Protocol.ChunkSize).through(parser.bufferToPacket)
      s <- assembler.eval(packet)
      _ <- Stream.eval(Logger[F].info(s"Server got the packet $packet and evaluated the result as: $s"))
    } yield s

    retValues.through(parser.packetToBuffer).to(socket.writes()).onFinalize(socket.endOfOutput)
  }

  def bindAndHandle: Stream[F, Unit] = SocketServerStream[F].handle(reactor)
}

private[task4s] class ClientImpl[F[_]: Concurrent: ContextShift](implicit val acg: AsynchronousChannelGroup) {

  private val parser = new ProtocolParser[F]

  private implicit val log: SelfAwareStructuredLogger[F] = Slf4jLogger.unsafeCreate[F]

  def writeN(rmt: TcpSocketConfig, source: Stream[F, Packet], signal: SignallingRef[F, Boolean]): Stream[F, Packet] = {
    def cycle(queue: Queue[F, Packet], signal: SignallingRef[F, Boolean]): Stream[F, Unit] =
      remote(rmt) { socket =>
        val writes = source.through(parser.packetToBuffer).to(socket.writes()).onFinalize(socket.endOfOutput)

        val reads = socket
          .reads(Protocol.ChunkSize)
          .through(parser.bufferToPacket)
          .to(queue.enqueue)

        writes ++ reads
      }

    val stream = for {
      q <- Stream.eval(Queue.bounded[F, Packet](10))
      packet <- q.dequeue concurrently cycle(q, signal)
      _ <- Stream.eval(Logger[F].info(s"Client get the return packet $packet"))
    } yield packet

    stream.interruptWhen(signal)
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
private[task4s] class NetService[F[_]: Concurrent: ContextShift](implicit val acg: AsynchronousChannelGroup) {
  def bindAndHandle(assembler: Assembler[F]): Stream[F, Unit] = new ServerImpl[F](assembler).bindAndHandle

  def writeN(rmt: TcpSocketConfig, source: Stream[F, Packet], signal: SignallingRef[F, Boolean]): Stream[F, Packet] =
    new ClientImpl[F].writeN(rmt, source, signal)
}

private[task4s] object NetService {
  def address: TcpSocketConfig = SocketServerStream.ServerSocketConfig

  def apply[F[_]: Concurrent: ContextShift](implicit acg: AsynchronousChannelGroup) = new NetService[F]()
}
