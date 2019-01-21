package machines.cluster

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift}
import fs2.{RaiseThrowable, Stream}
import machines.internal.MachineRepository
import machines.{Machine, Strategy}
import machines.internal.remote.tcp.TcpSocketConfig

import scala.util.Random

class CoordinatorProxy[F[_]: RaiseThrowable: Concurrent: ContextShift](val coordinators: Seq[TcpSocketConfig],
                                                                       private val repository: MachineRepository[F]) {

  import CoordinatorProxy._
  import CoordinationProtocol._

  type UnsafeMachine = Machine[F, _, _]

  private val indexedCoordinators = coordinators.toArray

  def allocate[I, O](machine: Machine[F, I, O], strategy: Strategy): Stream[F, Unit] = ???
  // NetService[F].writeN(selectCoordinator(indexedCoordinators))

  def handle(protocol: CoordinatorToProxy): Stream[F, ProxyToCoordinator] =
    protocol match {
      case cmd: Command => handleCommand(cmd)
      case _ => throw new IllegalAccessError("Show not access here for other protocol subtype.")
    }

  private def handleCommand(command: Command): Stream[F, ProxyToCoordinator] =
    command match {
      case AllocationCommand(machine) =>
        Stream
          .eval(repository.allocate(machine.channel, machine.asInstanceOf[UnsafeMachine]))
          .map(_ => CommandOk(machine.channel))

      case RemovalCommand(channel) =>
        Stream.eval(repository.remove(channel)).map(_ => CommandOk(channel))
    }
}

object CoordinatorProxy {

  def apply[F[_]: RaiseThrowable: Concurrent: ContextShift](
      coordinators: Seq[TcpSocketConfig],
      repository: MachineRepository[F]
  ): CoordinatorProxy[F] =
    new CoordinatorProxy(coordinators, repository)

  private def selectCoordinator(coordinators: Array[TcpSocketConfig]): TcpSocketConfig = {
    val size = coordinators.length
    val rand = new Random(System.currentTimeMillis())
    val idx = rand.nextInt(size)
    coordinators(idx)
  }
}
