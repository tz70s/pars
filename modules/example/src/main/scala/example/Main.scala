package example

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ExitCode, IO, IOApp}
import pars.{ParEffect, Pars}
import pars.cluster.internal.StandAloneCoordinator
import pars.internal.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}

object Main extends IOApp {

  val CoordinatorAddress = TcpSocketConfig("localhost", 9981)

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance()

  implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(CoordinatorAddress)

  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- Pars.service(PlusOne.run)(pe)(StandAloneCoordinator[IO](CoordinatorAddress)).compile.drain
    } yield ExitCode.Success
}
