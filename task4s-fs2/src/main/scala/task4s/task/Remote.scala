package task4s.task

import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import task4s.remote.Service
import task4s.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}
import cats.syntax.apply._
import fs2.{Chunk, Stream}
import fs2.io.tcp.Socket
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import task4s.remote.serialize.{Message, SerializationProvider}

import scala.concurrent.duration._

object Remote extends IOApp {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)
  implicit val log = Slf4jLogger.unsafeCreate[IO]

  val serializer = SerializationProvider.serializer

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
    Service
      .remote[IO](TcpSocketConfig("127.0.0.1", 8080), loopSeq(_))
      .compile
      .drain *> IO.pure(ExitCode.Success)
}
