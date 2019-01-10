package task4s.task

import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import task4s.remote.tcp.AsyncChannelProvider
import cats.syntax.apply._
import task4s.remote.Service

object Main extends IOApp {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)

  override def run(args: List[String]): IO[ExitCode] =
    Service[IO].compile.drain *> IO.pure(ExitCode.Success)
}
