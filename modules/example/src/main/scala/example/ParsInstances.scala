package example

import cats.effect.{IO, Sync}
import fs2.Stream
import pars.{ParEffect, Pars}

object ParsInstances {

  def liftValuesPars[F[_]: ParEffect] = Pars(1, 2, 3)

  def liftEffectivePars[F[_]: ParEffect: Sync] = Pars(Stream.eval(Sync[F].delay(println("Hello World!"))))
}
