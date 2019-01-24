package pars

import cats.effect.{IO, Timer}
import fs2.Stream
import pars.cluster.internal.StandAloneCoordinator

import scala.concurrent.duration._

class ChannelSpec extends NetParsSpec {

  "Channel" should {

    "work with send method" in {
      val coordinator = StandAloneCoordinator[IO](StandAloneCoordinatorAddress)

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(StandAloneCoordinatorAddress)

      val source = Stream(1, 2, 3, 4, 5)

      val send = TestChannel.send[IO, Int](source) concurrently Pars.spawn(TestPars)

      IO.race(Timer[IO].sleep(5.seconds), Pars.service(send)(pe)(coordinator).compile.drain).unsafeRunSync()
    }

    "work with receive method" in {
      val coordinator = StandAloneCoordinator[IO](StandAloneCoordinatorAddress)
      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(StandAloneCoordinatorAddress)

      val channel = Channel[Int]("receiver")

      val par = Pars(channel)(Stream(1, 2, 3, 4, 5).covary[IO])

      val out = channel.receive[IO]

      val f = out concurrently Pars.serveM(par)

      val result = Pars.service(f)(pe)(coordinator)

      IO.race(Timer[IO].sleep(5.seconds), result.compile.toList).unsafeRunSync() shouldBe Right(List(1, 2, 3, 4, 5))
    }
  }
}
