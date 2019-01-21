package machines.internal

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import fs2.{Pipe, RaiseThrowable, Stream}
import machines.cluster.CoordinationProtocol.CoordinatorToProxy
import machines.cluster.CoordinatorProxy
import machines.internal.Protocol.{ChannelProtocol, Protocol}
import machines.internal.remote.NetService
import machines.internal.remote.tcp.TcpSocketConfig
import machines.{Channel, FlyingMachine, Machine, Strategy}

import scala.collection.concurrent.TrieMap

private[machines] class ProtocolF[F[_]: Concurrent: ContextShift: Timer: RaiseThrowable](
    coordinators: Seq[TcpSocketConfig]
)(
    implicit acg: AsynchronousChannelGroup
) {

  private val repository = new MachineRepository[F]
  private val proxy = CoordinatorProxy(coordinators, repository)
  private val channelF = ChannelF(repository)

  private def logic: Pipe[F, Protocol, Protocol] = { from =>
    from.flatMap {
      case c: CoordinatorToProxy => proxy.handle(c)
      case event: ChannelProtocol => channelF.handle(event)
    }
  }

  private val background = Stream(proxy.bind).parJoinUnbounded

  def allocate[I, O](machine: FlyingMachine[F, I, O], strategy: Strategy): Stream[F, Protocol] =
    proxy.allocate(machine, strategy)

  def bindAndHandle: Stream[F, Unit] = NetService[F].bindAndHandle(logic).concurrently(background)
}

object ProtocolF {

  def bindAndHandle[F[_]: Concurrent: ContextShift: Timer](
      coordinators: Seq[TcpSocketConfig]
  )(implicit acg: AsynchronousChannelGroup): Stream[F, Unit] =
    new ProtocolF[F](coordinators).bindAndHandle
}

private[machines] class MachineRepository[F[_]: Sync] {

  import ChannelF._

  type UnsafeMachine = Machine[F, _, _]

  private val table = TrieMap[UnsafeChannel, UnsafeMachine]()

  def allocate(channel: UnsafeChannel, machine: UnsafeMachine): F[Unit] =
    Sync[F].delay(table += channel -> machine)

  def remove(channel: UnsafeChannel): F[Unit] =
    Sync[F].delay(table -= channel)

  def lookUp(channel: Channel[_]): F[Option[UnsafeMachine]] =
    Sync[F].delay(table.get(channel))
}

object Protocol {

  /**
   * There are two types of protocol.
   *
   * 1. Control plane: coordination protocol.
   * 2. Data plane: channel protocol
   */
  trait Protocol

  sealed trait ChannelProtocol extends Protocol

  // Main data event abstraction.
  case class Event[F[_], T](to: Channel[T], events: Stream[F, T]) extends ChannelProtocol

  // TODO should revisit this signature later.
  case class EventOk[T](value: T) extends ChannelProtocol
  case class EventErr(throwable: Throwable) extends ChannelProtocol
}
