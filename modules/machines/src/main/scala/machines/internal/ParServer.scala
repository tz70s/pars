package machines.internal

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Timer}
import fs2.{Pipe, RaiseThrowable, Stream}
import machines.cluster.CoordinationProtocol.CoordinatorToProxy
import machines.cluster.CoordinatorProxy
import machines.internal.Protocol.{ChannelProtocol, Event, Protocol}
import machines.internal.remote.NetService
import machines.internal.remote.tcp.TcpSocketConfig
import machines._

private[machines] class ParServer[F[_]: Concurrent: ContextShift: Timer: RaiseThrowable](
    coordinators: Seq[TcpSocketConfig]
)(
    implicit acg: AsynchronousChannelGroup
) {

  private val repository = new ChannelRoutingTable[F]
  private val proxy = CoordinatorProxy(coordinators, repository)
  private val router = ChannelRouter(repository)

  def allocate[I, O](machine: FlyingMachine[F, I, O], strategy: Strategy): Stream[F, Protocol] =
    proxy.allocate(machine, strategy)

  def send[T, I](to: Channel[T], event: Stream[F, I]): Stream[F, Unit] = router.send(Event(to, event))

  def bindAndHandle: Stream[F, Unit] = NetService[F].bindAndHandle(logic).concurrently(background)

  private def logic: Pipe[F, Protocol, Protocol] = { from =>
    from.flatMap {
      case c: CoordinatorToProxy => proxy.handle(c)
      case event: ChannelProtocol => router.receive(event)
    }
  }

  private val background = Stream(proxy.bind).parJoinUnbounded
}

object ParServer {

  def bindAndHandle[F[_]: Concurrent: ContextShift: Timer](
      coordinators: Seq[TcpSocketConfig]
  )(implicit acg: AsynchronousChannelGroup): Stream[F, Unit] =
    new ParServer[F](coordinators).bindAndHandle
}

object Protocol {

  /**
   * There are two types of protocol.
   *
   * 1. Control plane: coordination protocol.
   * 2. Data plane: channel protocol
   */
  trait Protocol extends Serializable

  sealed trait ChannelProtocol extends Protocol

  // Main data event abstraction.
  case class Event[F[_], T](to: Channel[T], events: Stream[F, T]) extends ChannelProtocol

  // TODO should revisit this signature later.
  case class EventOk[T](value: T) extends ChannelProtocol
  case class EventErr(throwable: Throwable) extends ChannelProtocol
}
