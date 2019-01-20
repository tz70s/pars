package example

import cats.effect.Sync
import task4s.{Forge, Machine}
import fs2.Stream

object Machines {

  def liftValuesMachine[F[_]: Forge] = Machine(1, 2, 3)

  def liftEffectiveMachine[F[_]: Forge: Sync] = Machine(Stream.eval(Sync[F].delay(println("Hello World!"))))

}
