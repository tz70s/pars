package task4s.task

import cats.effect._
import task4s.remote.Service
import task4s.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}
import cats.syntax.apply._
import fs2.{Chunk, Stream}
import fs2.io.tcp.Socket
import task4s.remote.serialize.NativeMessage

import scala.concurrent.duration._

object Remote extends IOApp {

  implicit val acg = AsyncChannelProvider.get(8)

  def eval: Stream[IO, Unit] = Stream.eval(IO(println("Hello world!")))

  def message: NativeMessage = NativeMessage.fromStream[IO, Unit](eval)

  def loopSeq(socket: Socket[IO]): Stream[IO, Unit] = {
    val throttle = for {
      _ <- Stream.eval(IO.sleep(100.millis))
      chunk <- Stream.chunk(Chunk.bytes(message.toBinary))
    } yield chunk
    throttle.repeat.through(socket.writes())
  }

  override def run(args: List[String]): IO[ExitCode] =
    Service
      .remote[IO](TcpSocketConfig("127.0.0.1", 8080), loopSeq(_))
      .compile
      .drain *> IO.pure(ExitCode.Success)
}
