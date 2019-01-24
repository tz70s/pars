package example.pure

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ExitCode, IO, IOApp}
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import pars.cluster.internal.StandAloneCoordinator
import pars.internal.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}
import pars.{ParEffect, Pars}

object Main extends IOApp {

  implicit val log: Logger[IO] = Slf4jLogger.unsafeCreate[IO]

  val CoordinatorAddress = TcpSocketConfig("localhost", 9981)

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance()

  implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(CoordinatorAddress)

  def parse(args: List[String]): IO[String] =
    args match {
      case head :: Nil => IO.pure(head)
      case _ => IO.raiseError(new IllegalArgumentException("Only accept one argument for the running app type."))
    }

  def serve(name: String): Stream[IO, Unit] =
    name match {
      case "never" => Stream.eval(IO.never)
      case "pure" => PlusOne.run.drain
      case "throughput" => Stream.empty
      case _ => Stream.empty
    }

  def service(logic: Stream[IO, Unit]): IO[Unit] =
    Pars.service(logic)(pe)(StandAloneCoordinator[IO](CoordinatorAddress)).compile.drain

  override def run(args: List[String]): IO[ExitCode] =
    for {
      name <- parse(args)
      _ <- service(serve(name))
    } yield ExitCode.Success
}
