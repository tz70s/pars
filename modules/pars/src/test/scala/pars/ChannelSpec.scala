package pars

import cats.effect.IO
import fs2.Stream
import pars.cluster.internal.StandAloneCoordinator

import cats.implicits._

class ChannelSpec extends NetParsSpec {

  "Channel" should {

    "work with pub method" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinators(Seq(StandAloneCoordinatorAddress))
      val parServer = pe.server

      val background = Stream(parServer.bindAndHandle, coordinator).parJoin(2)
      val run = parServer.allocate(TestPars, Strategy(1)) concurrently background

      val source = Stream(1, 2, 3, 4, 5)

      val pub = TestChannel.pub[IO, Int](source)

      (run *> pub).compile.drain.unsafeRunSync()
    }
  }
}
