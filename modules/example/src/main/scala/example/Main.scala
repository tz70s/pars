package example

import cats.effect.{ExitCode, IO, IOApp}
import pars.ParEffect
import pars.cluster.internal.StandAloneCoordinator
import pars.internal.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}

object Main extends IOApp {

  implicit val acg = AsyncChannelProvider.instance(8)

  val coordinatorAddress = TcpSocketConfig("localhost", 9981)
  val coordinator = new StandAloneCoordinator[IO]

  implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinators(Seq(coordinatorAddress))

  override def run(args: List[String]): IO[ExitCode] =
    IO.pure(ExitCode.Success)
}
