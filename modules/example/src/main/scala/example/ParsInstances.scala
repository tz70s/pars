package example

import cats.effect.{IO, Sync}
import fs2.Stream
import pars.{ParEffect, Pars}

import pars.dsl._
import cats.implicits._

object ParsInstances {

  def offloadPars(implicit pe: ParEffect[IO]): Pars[IO, Unit, Int] = Pars(5, 6, 7).map(_ + 1)

  def liftValuesPars[F[_]: ParEffect] = Pars(1, 2, 3)

  def liftEffectivePars[F[_]: ParEffect: Sync] = Pars(Stream.eval(Sync[F].delay(println("Hello World!"))))

}
