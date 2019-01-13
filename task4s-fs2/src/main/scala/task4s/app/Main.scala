package task4s.app

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ExitCode, IO, IOApp}
import task4s.remote.Service
import task4s.remote.tcp.AsyncChannelProvider
import cats.implicits._

object Main extends IOApp {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)

  override def run(args: List[String]): IO[ExitCode] =
    Service[IO].compile.drain *> IO.pure(ExitCode.Success)
}
