package example

import cats.effect.{ExitCode, IO, IOApp}
import pars.ParEffect
import pars.cluster.internal.StandAloneCoordinator
import pars.internal.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}

import fs2.Stream

object Main extends IOApp {

  implicit val acg = AsyncChannelProvider.instance(8)

  val coordinatorAddress = TcpSocketConfig("localhost", 9981)

  val coordinator = StandAloneCoordinator[IO]

  implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinators(Seq(coordinatorAddress))

  def bindings: Stream[IO, Unit] =
    Stream(coordinator.bindAndHandle(coordinatorAddress)).parJoinUnbounded

  override def run(args: List[String]): IO[ExitCode] =
    IO.pure(ExitCode.Success)
}
