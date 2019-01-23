package pars.dsl

import cats.effect.{IO, Timer}
import pars.{NetParsSpec, ParEffect, Pars}
import cats.implicits._
import pars.cluster.internal.StandAloneCoordinator
import fs2.Stream

import scala.concurrent.duration._

class MonadSpec extends NetParsSpec {

  "Pars Monad" should {

    "concatenate computation instances" in {

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(StandAloneCoordinatorAddress)

      val pars = Stream.emits(1 to 10).covary[IO].pars.flatMap { i =>
        Pars(Stream.eval(IO { println(s"Successfully launch pars instance, mapping value $i"); i }))
      }

      val background =
        Stream(StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)).parJoinUnbounded

      val spawnAndFire = Pars.bind(pars).flatMap(c => c.pub(Stream.empty.covary[IO]))

      val timeout = Timer[IO].sleep(3.seconds)

      IO.race(timeout, spawnAndFire.concurrently(background).compile.drain).unsafeRunSync()
    }
  }
}
