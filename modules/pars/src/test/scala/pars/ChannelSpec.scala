package pars

import cats.effect.{IO, Timer}
import fs2.Stream
import pars.cluster.internal.StandAloneCoordinator
import cats.implicits._

import scala.concurrent.duration._

class ChannelSpec extends NetParsSpec {

  "Channel" should {

    /*
    "work with unsafeSend method" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinators(Seq(StandAloneCoordinatorAddress))

      val server = pe.server
      val background = Stream(server.bindAndHandle, coordinator).parJoin(2)

      val run = server.spawn(TestPars) concurrently background

      val source = Stream(1, 2, 3, 4, 5)

      val unsafeSend = TestChannel.unsafeSend[IO, Int](source)

      IO.race(Timer[IO].sleep(5.seconds), (run *> unsafeSend).compile.drain).unsafeRunSync()
    }
     */

    "work with receive method" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(StandAloneCoordinatorAddress)

      val channel = Channel[Int]("receiver")

      val par = Pars(channel)(Stream(1, 2, 3, 4, 5).covary[IO])

      val background = Stream(pe.server.bindAndHandle,
                              coordinator,
                              pe.spawn(par).flatMap(c => pe.send(c, Stream.empty.covary[IO]))).parJoinUnbounded

      val out = channel.receive[IO]

      val result = out concurrently background

      IO.race(Timer[IO].sleep(5.seconds), result.compile.toList).unsafeRunSync() shouldBe Right(List(1, 2, 3, 4, 5))
    }
  }
}
