package pars

import cats.effect.{IO, Timer}
import fs2.Stream
import pars.cluster.internal.StandAloneCoordinator

import scala.concurrent.duration._

import cats.implicits._

class ChannelSpec extends NetParsSpec {

  "Channel" should {

    "work with send method" in {
      val coordinator = StandAloneCoordinator[IO](StandAloneCoordinatorAddress)

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(StandAloneCoordinatorAddress)

      val source = Stream(1, 2, 3, 4, 5)

      val in = Channel[Int]("test-in")
      val out = Channel[Int]("test-out")

      val pars = Pars.concat(in, out) { from: Stream[IO, Int] =>
        for {
          i <- from
          plus = i + 1
          _ <- Stream.eval(IO { println(s"Get the number of $i, and map it to $plus") })
        } yield plus
      }

      val send = Pars.spawn(pars) *> in.send[IO, Int](source)
      val receive = out.receive[IO]

      IO.race(Timer[IO].sleep(5.seconds), Pars.service(receive concurrently send)(pe)(coordinator).compile.toList)
        .unsafeRunSync() shouldBe Right(List(2, 3, 4, 5, 6))
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
