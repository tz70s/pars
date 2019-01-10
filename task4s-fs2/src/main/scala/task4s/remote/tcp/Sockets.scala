package task4s.remote.tcp

import pureconfig.generic.auto._
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.Executors

import cats.effect.{Concurrent, ContextShift, Sync}
import com.typesafe.scalalogging.Logger
import fs2.Stream
import fs2.io.tcp.Socket

case class TcpSocketConfig(hostname: String, port: Int)

/**
 * Similar to Akka.
 *
 * We'll build a dual visible channels for peers, but composed via Stream.
 * Therefore, both server and client should be transparent to user.
 */
class SocketServerStream[F[_]: Concurrent: ContextShift](
    config: TcpSocketConfig = pureconfig.loadConfigOrThrow[TcpSocketConfig]("task4s.remote.tcp")
)(implicit acg: AsynchronousChannelGroup) {

  private val log = Logger(this.getClass)

  val address = new InetSocketAddress(config.hostname, config.port)

  private def sockets: Stream[F, Socket[F]] =
    for {
      _ <- Stream.eval(Sync[F].delay(log.info(s"Start tcp server binding in address : $address")))
      resource <- Socket.server[F](address)
      socket <- Stream.resource(resource)
    } yield socket

  def ofStream(handler: Socket[F] => Stream[F, Unit]): Stream[F, Unit] =
    sockets.map(socket => handler(socket)).parJoinUnbounded
}

class SocketClientStream[F[_]: Concurrent: ContextShift](remote: TcpSocketConfig)(
    implicit acg: AsynchronousChannelGroup
) {
  val address = new InetSocketAddress(remote.hostname, remote.port)

  def sockets: Stream[F, Socket[F]] =
    Stream.resource(Socket.client[F](address))

  def ofStream(handler: Socket[F] => Stream[F, Unit]): Stream[F, Unit] =
    sockets.map(socket => handler(socket)).parJoinUnbounded
}

object SocketServerStream {
  def apply[F[_]: Concurrent: ContextShift](
      handler: Socket[F] => Stream[F, Unit]
  )(implicit acg: AsynchronousChannelGroup): Stream[F, Unit] =
    new SocketServerStream[F]().ofStream(handler)
}

object SocketClientStream {
  def apply[F[_]: Concurrent: ContextShift](remote: TcpSocketConfig, handler: Socket[F] => Stream[F, Unit])(
      implicit acg: AsynchronousChannelGroup
  ): Stream[F, Unit] = new SocketClientStream[F](remote).ofStream(handler)
}

object AsyncChannelProvider {

  val DefaultNrOfThreads = 8

  def instance(nrOfThreads: Int = DefaultNrOfThreads): AsynchronousChannelGroup =
    AsynchronousChannelProvider.provider().openAsynchronousChannelGroup(nrOfThreads, Executors.defaultThreadFactory())
}
