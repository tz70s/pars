package machines.internal

import fs2.{RaiseThrowable, Stream}
import machines._
import machines.internal.UnsafeFacade.OutGoing
import machines.internal.UnsafeFacade.Signal.Spawn

class ParEffectImpl[F[_]: RaiseThrowable](facade: UnsafeFacade[F]) extends ParEffect[F] {

  override def spawn[I, O](machine: Machine[F, I, O], strategy: Strategy): Stream[F, O] = machine match {
    case localSource: MachineSource[F, O] => localSource.evaluateToStream
    case flyingSource: FlyingMachineSource[F, O] => flyingSource.evaluateToStream

    case flying: FlyingMachine[F, I, O] =>
      facade.handlePacket(Spawn(flying)).flatMap {
        case OutGoing.Ok => Stream.empty
        case _ => Stream.raiseError[F](new Exception("Unexpected error when spawning machine"))
      }
  }
}

object ParEffectImpl {
  def apply[F[_]: RaiseThrowable](facade: UnsafeFacade[F]): ParEffectImpl[F] = new ParEffectImpl(facade)
}
