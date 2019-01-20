package machines.internal.remote.tcp

import pureconfig.generic.auto._
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.Executors

import cats.effect.{Concurrent, ContextShift}
import fs2.Stream
import fs2.io.tcp.Socket
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

case class TcpSocketConfig(hostname: String, port: Int)

/**
 * We'll build a dual visible channels for peers, but composed via Stream.
 * Therefore, both server and client should be transparent to user.
 *
 * @example {{{
 * val stream = SocketServerStream[IO].handle { socket => socket.reads(1024).through(extractor) }
 * }}}
 */
private[remote] class SocketServerStream[F[_]: Concurrent: ContextShift](implicit acg: AsynchronousChannelGroup) {

  import SocketServerStream._

  private implicit val log: SelfAwareStructuredLogger[F] = Slf4jLogger.unsafeCreate[F]

  private val address = new InetSocketAddress(ServerSocketConfig.hostname, ServerSocketConfig.port)

  private def sockets: Stream[F, Socket[F]] =
    for {
      _ <- Stream.eval(Logger[F].info(s"Start tcp server binding in address : $address"))
      resource <- Socket.server[F](address)
      socket <- Stream.resource(resource)
    } yield socket

  def handle(handler: Socket[F] => Stream[F, Unit]): Stream[F, Unit] =
    sockets.map(socket => handler(socket)).parJoinUnbounded
}

private[remote] class SocketClientStream[F[_]: Concurrent: ContextShift]()(
    implicit acg: AsynchronousChannelGroup
) {
  private def connection(address: InetSocketAddress): Stream[F, Socket[F]] =
    Stream.resource(Socket.client[F](address))

  def handle(remote: TcpSocketConfig, handler: Socket[F] => Stream[F, Unit]): Stream[F, Unit] = {
    val address = new InetSocketAddress(remote.hostname, remote.port)
    connection(address).map(socket => handler(socket)).parJoinUnbounded
  }
}

object SocketServerStream {

  val ServerSocketConfig: TcpSocketConfig = pureconfig.loadConfigOrThrow[TcpSocketConfig]("machines.remote.tcp")

  def apply[F[_]: Concurrent: ContextShift](implicit acg: AsynchronousChannelGroup): SocketServerStream[F] =
    new SocketServerStream[F]()
}

object SocketClientStream {
  def apply[F[_]: Concurrent: ContextShift](implicit acg: AsynchronousChannelGroup): SocketClientStream[F] =
    new SocketClientStream[F]()
}

object AsyncChannelProvider {

  private case class DefaultWorkStealingPoolSizeConfig(min: Int, max: Int)

  private val DefaultWorkStealingPoolSize =
    pureconfig.loadConfigOrThrow[DefaultWorkStealingPoolSizeConfig]("machines.remote.thread-pool.default")

  /**
   * By default use the work stealing thread pool for async channel.
   * Note that this is not ideal for blocking operations, which we should avoid.
   *
   * @param maxNrOfThreads Maximum number of threads in thread pool, default is 16 threads.
   * @param initNrOfThreads Initial number of threads in thread pool, default is 4 threads.
   * @return Asynchronous channel group for NIO invocation.
   */
  def instance(maxNrOfThreads: Int = DefaultWorkStealingPoolSize.max,
               initNrOfThreads: Int = DefaultWorkStealingPoolSize.min): AsynchronousChannelGroup =
    AsynchronousChannelProvider
      .provider()
      .openAsynchronousChannelGroup(Executors.newWorkStealingPool(maxNrOfThreads), initNrOfThreads)
}
