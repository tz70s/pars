package machines.internal

import cats.effect.Sync
import fs2.Stream
import machines.internal.Protocol.{ChannelProtocol, Event, EventOk}
import machines.internal.remote.tcp.TcpSocketConfig
import machines.{Channel, Machine}

/**
 * Note that this is a '''DARK SIDE''' behavior which contains unavoidable ''Any'' cast and required high encapsulation.
 *
 * For remotely assemble machine, we lacks of type information for '''return type''', and also we '''don't''' really need it.
 *
 * However, the assembled stream should be cast back to normal type after evaluation at the call side or composition point.
 */
private[machines] class ChannelRouter[F[_]: Sync](val repository: MachineRepository[F]) {

  import ChannelRouter._

  type UnsafeMachine = Machine[F, _, _]

  def handle(event: ChannelProtocol): Stream[F, ChannelProtocol] =
    event match {
      case Event(to, events: Stream[F, _]) => process(events, to).map(v => EventOk(v))
      case _ => throw new IllegalArgumentException(s"Unexpected protocol subtype to handle here.")
    }

  private def process(stream: Stream[F, _], channel: UnsafeChannel): Stream[F, _] =
    for {
      o <- Stream.eval(repository.lookUp(channel))
      m <- try Stream.emit(o.get)
      catch {
        case _: Throwable => Stream.raiseError(MachineNotFoundException(s"Not found machine with channel $channel"))
      }
      s <- m.asInstanceOf[Machine[F, Any, _]].evaluateToStream(stream)
    } yield s
}

private[machines] object ChannelRouter {

  case class ChannelRoutee(seq: Seq[TcpSocketConfig])

  def apply[F[_]: Sync](repository: MachineRepository[F]): ChannelRouter[F] = new ChannelRouter(repository)

  type UnsafeChannel = Channel[_]
}
