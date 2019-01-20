package example

import cats.effect.Sync
import fs2.Stream
import machines.{Machine, ParEffect}

object Machines {

  def liftValuesMachine[F[_]: ParEffect] = Machine(1, 2, 3)

  def liftEffectiveMachine[F[_]: ParEffect: Sync] = Machine(Stream.eval(Sync[F].delay(println("Hello World!"))))

}
