package pars.internal

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Sync}
import fs2.Stream
import pars.internal.Protocol.{ChannelProtocol, Event, EventOk}
import pars.internal.remote.NetService
import pars.internal.remote.tcp.TcpSocketConfig
import pars.{Channel, ChannelOutputStrategy, Pars}

import scala.collection.concurrent.TrieMap
import scala.util.Random
import scala.util.control.NonFatal

/**
 * Note that this is a '''DARK SIDE''' behavior which contains unavoidable ''Any'' cast and required high encapsulation.
 *
 * For remotely process pars, we lacks of type information for '''return type''', and also we '''don't''' really need it.
 *
 * However, the processed stream should be cast back to normal type after evaluation at the call side or composition point.
 */
private[pars] class ChannelRouter[F[_]: Concurrent: ContextShift](val repository: ChannelRoutingTable[F])(
    implicit acg: AsynchronousChannelGroup
) {

  def send(event: ChannelProtocol): Stream[F, Unit] =
    event match {
      case evt: Event[F, _] =>
        val policy = evt.to.strategy
        for {
          entry <- repository.lookUp(evt.to)
          s <- policy match {
            case ChannelOutputStrategy.Concurrent => unicast(entry, evt)
            case ChannelOutputStrategy.Broadcast => broadcast(entry, evt)
          }
        } yield s

      case _ => throw new IllegalArgumentException(s"Unexpected protocol subtype to receive here.")
    }

  private def unicast(entry: ChannelRouteEntry[F], event: Event[F, _]): Stream[F, Unit] = {
    require(entry.endpoints.nonEmpty)
    val index = Random.nextInt(entry.endpoints.size)
    val address = entry.endpoints.iterator.drop(index).next()
    NetService[F].backOffWriteN(address, Stream.emit(event)).drain
  }

  private def broadcast(entry: ChannelRouteEntry[F], event: Event[F, _]): Stream[F, Unit] =
    Stream
      .emits(entry.endpoints)
      .map { address =>
        NetService[F].backOffWriteN(address, Stream.emit(event))
      }
      .parJoinUnbounded
      .drain

  def receive(event: ChannelProtocol): Stream[F, ChannelProtocol] =
    event match {
      case Event(to, events: Stream[F, _]) => process(events, to).map(v => EventOk(v))
      case _ => throw new IllegalArgumentException(s"Unexpected protocol subtype to receive here.")
    }

  /**
   * Note that we can only process FlyingPars here.
   *
   * The other pars type has no receiver channel, hence it's not possible handle by remote functional process.
   *
   * The other pitfall is, there's no return type after evaluation actually.
   */
  private def process(stream: Stream[F, _], channel: UnsafeChannel): Stream[F, _] =
    for {
      entry <- repository.lookUp(channel)
      s <- entry.machine.asInstanceOf[Pars[F, Any, _]].evaluateToStream(stream)
    } yield s
}

private[pars] object ChannelRouter {

  def apply[F[_]: Concurrent: ContextShift](repository: ChannelRoutingTable[F])(
      implicit acg: AsynchronousChannelGroup
  ): ChannelRouter[F] = new ChannelRouter(repository)

}

case class ChannelRouteEntry[F[_]](machine: Pars[F, _, _], endpoints: Seq[TcpSocketConfig])

private[pars] class ChannelRoutingTable[F[_]: Sync] {

  private val table = TrieMap[UnsafeChannel, ChannelRouteEntry[F]]()

  def allocate(machine: UnsafePars[F], endpoints: Seq[TcpSocketConfig]): Stream[F, Unit] =
    Stream.eval(Sync[F].delay {
      table += (machine.channel -> ChannelRouteEntry(machine, endpoints))
      ()
    })

  def remove(channel: UnsafeChannel): Stream[F, Unit] =
    Stream.eval(Sync[F].delay { table -= channel; () })

  def lookUp(channel: Channel[_]): Stream[F, ChannelRouteEntry[F]] =
    for {
      option <- Stream.eval(Sync[F].delay(table.get(channel)))
      entry <- try { Stream.emit(option.get) } catch {
        case NonFatal(_) => Stream.raiseError(ParsNotFoundException(s"Can't find pars for channel $channel"))
      }
    } yield entry
}
