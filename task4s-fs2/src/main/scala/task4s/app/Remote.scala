package task4s.app

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ExitCode, IO, IOApp}
import fs2.io.tcp.Socket
import fs2.{Chunk, Stream}
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import task4s.remote.serialize.{SerializationProvider, Serializer}
import task4s.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}
import task4s.remote.{Message, Service}
import cats.implicits._

import scala.concurrent.duration._

object Remote extends IOApp {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)
  implicit val log: SelfAwareStructuredLogger[IO] = Slf4jLogger.unsafeCreate[IO]

  val serializer: Serializer = SerializationProvider.serializer

  def eval: Stream[IO, Unit] = Stream.eval(Logger[IO].info("Hello world!"))

  def message: Message = Message.fromStream[IO, Unit](eval)

  def loopSeq(socket: Socket[IO]): Stream[IO, Unit] = {
    val throttle = for {
      _ <- Stream.eval(IO.sleep(100.millis))
      binary <- serializer.toBinary(message) match {
        case Right(b) => Stream.emit(b)
        case Left(t) => Stream.raiseError[IO](t)
      }
      chunk <- Stream.chunk(Chunk.bytes(binary))
    } yield chunk

    throttle.repeat.through(socket.writes())
  }

  override def run(args: List[String]): IO[ExitCode] =
    IO.pure(ExitCode.Success)
}
