package task4s.task

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.Executors

import cats.effect._
import fs2.{text, Stream}
import cats.implicits._
import com.typesafe.scalalogging.Logger
import fs2.concurrent.Topic
import fs2.io.tcp.Socket

object Main extends IOApp {

  val log = Logger("IOApp")

  // For FS2 tcp socket, the nio channel group should be declared as provider.
  implicit val acg: AsynchronousChannelGroup =
    AsynchronousChannelProvider.provider().openAsynchronousChannelGroup(8, Executors.defaultThreadFactory())

  /**
   * Implementation of Tcp Server binding with simple print method.
   *
   * Tcp streaming via simple EOF delimiter. (the socket reads method)
   *
   * Can be tested via:
   *
   * {{{
   * nc localhost 7878
   * }}}
   *
   * FS2 lacks of comprehensive document, need to refer to test code for usage.
   *
   * [[https://github.com/functional-streams-for-scala/fs2/blob/series/1.0/io/src/test/scala/fs2/io/tcp/SocketSpec.scala]]
   *
   * @return Stream of IO effect and unit type.
   */
  def tcpServer: Stream[IO, Unit] = {
    val address = new InetSocketAddress("0.0.0.0", 7828)

    for {
      // Start server.
      _ <- Stream.eval(IO { log.info(s"Start tcp server at $address") })
      resource <- Socket.server[IO](address)
      socket <- Stream.resource(resource)
      remote <- Stream.eval(socket.remoteAddress)
      _ <- Stream.eval(IO { log.info(s"Get connection - $remote") })
      evtStream <- Stream.eval(Topic[IO, Event](Receiving("")))
      _ <- handlePeer(socket, evtStream)
    } yield ()
  }

  sealed trait Event
  case class Receiving(text: String) extends Event

  /**
   * Decoupling stream via topic stream.
   *
   * Seems that it's better to abstract over context to achieve correct usage in FS2.
   */
  def handlePeer[F[_]](socket: Socket[F], evtStream: Topic[F, Event])(implicit F: Concurrent[F]): Stream[F, Unit] = {
    val publish = evtStream.publish(socket.reads(1024).through(text.utf8Decode).through(text.lines).map(Receiving))
    val subscribe = evtStream.subscribe(10).flatMap(evt => Stream.eval(F.delay { println(evt) }))
    Stream(publish.concurrently(subscribe)).parJoin(2)
  }

  def run(args: List[String]): IO[ExitCode] =
    tcpServer.handleErrorWith(t => Stream.eval(IO { log.error(s"${t.getMessage}") })).compile.drain.as(ExitCode.Success)
}
