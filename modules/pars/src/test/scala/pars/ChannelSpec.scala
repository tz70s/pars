package pars

import cats.effect.{IO, Timer}
import fs2.Stream
import pars.cluster.internal.StandAloneCoordinator
import cats.implicits._

import scala.concurrent.duration._

class ChannelSpec extends NetParsSpec {

  "Channel" should {

    "work with pub method" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinators(Seq(StandAloneCoordinatorAddress))

      val parServer = pe.server
      val background = Stream(parServer.bindAndHandle, coordinator).parJoin(2)
      val run = parServer.spawn(TestPars) concurrently background

      val source = Stream(1, 2, 3, 4, 5)

      val pub = TestChannel.pub[IO, Int](source)

      (run *> pub).compile.drain.unsafeRunSync()
    }

    "work with sub method" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(StandAloneCoordinatorAddress)

      val channel = TestChannel(pe)

      val background =
        Stream(pe.server.bindAndHandle, coordinator, channel.pub[IO, Int](Stream(1, 2, 3))).parJoinUnbounded

      val out = channel.sub[IO].take(3)

      val result = out concurrently background

      result.compile.toList.unsafeRunSync() shouldBe List(1, 2, 3)
    }
  }
}
