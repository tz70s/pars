package task4s.internal

import task4s._
import fs2.{RaiseThrowable, Stream}
import task4s.internal.UnsafeFacade.OutGoing
import task4s.internal.UnsafeFacade.Signal.Spawn

class ForgeImpl[F[_]: RaiseThrowable](facade: UnsafeFacade[F]) extends Forge[F] {

  override def forge[I, O](machine: Machine[F, I, O], strategy: Strategy): Stream[F, O] = machine match {
    case localSource: MachineSource[F, O] => localSource.evaluateToStream
    case flyingSource: FlyingMachineSource[F, O] => flyingSource.evaluateToStream

    case flying: FlyingMachine[F, I, O] =>
      facade.eval(Spawn(flying)).flatMap {
        case OutGoing.Ok => Stream.empty
        case _ => Stream.raiseError[F](new Exception("Unexpected error when spawning machine"))
      }
  }
}

object ForgeImpl {
  def apply[F[_]: RaiseThrowable](facade: UnsafeFacade[F]): ForgeImpl[F] = new ForgeImpl(facade)
}
