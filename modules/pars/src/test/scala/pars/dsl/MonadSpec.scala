package pars.dsl

import cats.effect.IO
import pars.{Channel, NetParsSpec, ParEffect, Pars}
import cats.implicits._
import fs2.Stream

class MonadSpec extends NetParsSpec {

  "Pars Monad" should {

    "concatenate computation instances" in {

      implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(StandAloneCoordinatorAddress)

      val out = Channel[Int]("receiver")

      val pars = Stream.emits(0 to 10).covary[IO].pars.flatMap { i =>
        Pars(out)(Stream.eval(IO { println(s"Successfully launch pars instance, mapping value $i"); i }))
      }
    }
  }
}
