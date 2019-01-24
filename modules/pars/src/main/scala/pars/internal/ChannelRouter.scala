package pars.internal

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import fs2.{RaiseThrowable, Stream}
import fs2.concurrent.NoneTerminatedQueue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import pars.internal.Protocol.{ChannelProtocol, Event, EventErr, EventOk}
import pars.internal.remote.NetService
import pars.internal.remote.tcp.TcpSocketConfig
import pars.{Channel, ChannelOutputStrategy, Pars}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.util.Random
import scala.util.control.NonFatal
import scala.concurrent.duration._
import cats.implicits._
import pars.cluster.CoordinatorProxy

/**
 * Note that this is a '''DARK SIDE''' behavior which contains unavoidable ''Any'' cast and required high encapsulation.
 *
 * For remotely process pars, we lacks of type information for '''return type''', and also we '''don't''' really need it.
 *
 * However, the processed stream should be cast back to normal type after evaluation at the call side or composition point.
 */
private[pars] class ChannelRouter[F[_]: Concurrent: ContextShift: RaiseThrowable: Timer](
    val repository: ChannelRoutingTable[F],
    val proxy: CoordinatorProxy[F]
)(
    implicit acg: AsynchronousChannelGroup
) {

  implicit val log: Logger[F] = Slf4jLogger.unsafeCreate[F]

  private val receivers = new EvaluationReceivers[F]

  def subscribe[T](channel: Channel[T], queue: NoneTerminatedQueue[F, T]): Stream[F, Unit] =
    receivers.subscribe(channel, queue)

  def send(event: ChannelProtocol, blockUntilEntry: FiniteDuration = 500.millis): Stream[F, Unit] =
    event match {
      case evt: Event[F, _] =>
        val policy = evt.to.strategy
        if (policy == ChannelOutputStrategy.NotUsed) {
          Stream.empty
        } else {
          for {
            entry <- blockUntilEntryAvailable(evt.to)
            s <- policy match {
              case ChannelOutputStrategy.Concurrent => unicast(entry, evt)
              case ChannelOutputStrategy.Broadcast => broadcast(entry, evt)
              case _ => Stream.empty
            }
          } yield s
        }

      case _ => throw new IllegalArgumentException(s"Unexpected protocol subtype to receive here.")
    }

  private def blockUntilEntryAvailable(channel: UnsafeChannel,
                                       backOff: FiniteDuration = 100.millis,
                                       factors: Int = 2): Stream[F, ChannelRouteEntry[F]] =
    repository.lookUp(channel).handleErrorWith {
      case ParsNotFoundException(m) =>
        proxy
          .lookUpEntry(channel) *> blockUntilEntryAvailable(channel, backOff)
          .handleErrorWith { t: Throwable =>
            Stream.awakeDelay[F](backOff) *> blockUntilEntryAvailable(channel, backOff * factors)
          }
      case t => Stream.raiseError(t)
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
      case Event(to, events: Stream[F, _]) =>
        process(events, to)
          .map(v => EventOk(v))
          .handleErrorWith(t => Stream.emit(EventErr(t)))
      case _ => throw new IllegalArgumentException(s"Unexpected protocol subtype to receive here.")
    }

  private def process(stream: Stream[F, _], channel: UnsafeChannel): Stream[F, Unit] = {
    val result = for {
      entry <- repository.lookUp(channel)
      _ <- Stream.eval(Logger[F].trace(s"Get the entry $entry"))
    } yield (entry.pars.asInstanceOf[Pars[F, Any, _]].evaluateToStream(stream), entry)

    result.flatMap {
      case (s, entry) =>
        Stream(receivers.publish(channel, s.map(Some(_)) ++ Stream.emit(None)), send(Event(entry.pars.out, s)))
          .parJoin(2)
          .drain
    }
  }
}

private[pars] object ChannelRouter {

  def apply[F[_]: Concurrent: ContextShift: RaiseThrowable: Timer](repository: ChannelRoutingTable[F],
                                                                   proxy: CoordinatorProxy[F])(
      implicit acg: AsynchronousChannelGroup
  ): ChannelRouter[F] = new ChannelRouter(repository, proxy)
}

private[pars] class EvaluationReceivers[F[_]: Concurrent] {
  private val queues = TrieMap[UnsafeChannel, NoneTerminatedQueue[F, Any]]()

  def subscribe[T](channel: Channel[T], queue: NoneTerminatedQueue[F, T]): Stream[F, Unit] =
    Stream.eval(Sync[F].delay {
      queues += (channel -> queue.asInstanceOf[NoneTerminatedQueue[F, Any]])
      ()
    })

  def publish(channel: UnsafeChannel, values: Stream[F, Option[Any]]): Stream[F, Unit] =
    queues.get(channel) match {
      case Some(q) => q.enqueue(values)
      case _ => Stream.empty
    }

}

case class ChannelRouteEntry[F[_]](pars: Pars[F, _, _], endpoints: Seq[TcpSocketConfig])

private[pars] class ChannelRoutingTable[F[_]: Sync: RaiseThrowable] {

  private val table = TrieMap[UnsafeChannel, ChannelRouteEntry[F]]()

  def allocate(pars: UnsafePars[F], endpoints: Seq[TcpSocketConfig]): Stream[F, Unit] =
    Stream.eval(Sync[F].delay {
      table += (pars.in -> ChannelRouteEntry(pars, endpoints))
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
