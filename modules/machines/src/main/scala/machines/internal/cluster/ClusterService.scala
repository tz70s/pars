package machines.internal.cluster

import fs2.{RaiseThrowable, Stream}
import machines.{Channel, Machine, Strategy}
import machines.internal.{MachineSpawnRejectionException, UnsafeFacade}
import machines.internal.UnsafeFacade.OutGoing.Ok
import machines.internal.UnsafeFacade.Signal

abstract class ClusterService[F[_]: RaiseThrowable](facade: UnsafeFacade[F]) {

  def allocate[I, O](machine: Machine[F, I, O], strategy: Strategy): Stream[F, Unit]

  def registerChannel[T](channel: Channel[T]): Stream[F, Unit]

  def receiveSignal(): Stream[F, Unit]

  protected final def onSignal(signal: Signal): Stream[F, Unit] = facade.handlePacket(signal).flatMap {
    case Ok => Stream.empty
    case _ => Stream.raiseError(MachineSpawnRejectionException)
  }
}
