package pars.dsl

import cats.effect.{IO, Timer}
import pars.{Channel, NetParsSpec, ParEffect, Pars}
import cats.implicits._
import pars.cluster.internal.StandAloneCoordinator
import fs2.Stream

import scala.concurrent.duration._

class MonadSpec extends NetParsSpec {

  "Pars Monad" should {

    "concatenate computation instances" in {

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(StandAloneCoordinatorAddress)

      val out = Channel[Int]("receiver")

      val pars = Stream.emits(0 to 10).covary[IO].pars.flatMap { i =>
        Pars(out)(Stream.eval(IO { println(s"Successfully launch pars instance, mapping value $i"); i }))
      }

      val background = Stream(StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress),
                              Pars.bind(pars).flatMap(c => pe.send(c, Stream.empty.covary[IO]))).parJoinUnbounded

      val receive = out.receive[IO] concurrently background

      val timeout = Timer[IO].sleep(3.seconds)

      IO.race(timeout, receive.compile.toList).unsafeRunSync() shouldBe Right((0 to 10).toList)
    }
  }
}
