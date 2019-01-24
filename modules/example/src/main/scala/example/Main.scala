package example

import cats.effect.{ExitCode, IO, IOApp}
import pars.ParEffect
import pars.cluster.internal.StandAloneCoordinator
import pars.internal.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}

import fs2.Stream

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    IO.pure(ExitCode.Success)
}
