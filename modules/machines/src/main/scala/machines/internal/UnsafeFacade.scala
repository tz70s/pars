package machines.internal

import cats.effect.Sync
import fs2.Stream
import machines.{Channel, FlyingMachine, Machine}

import scala.collection.concurrent.TrieMap

/**
 * Note that this is a '''DARK SIDE''' behavior which contains unavoidable ''Any'' cast and required high encapsulation.
 *
 * For remotely assemble machine, we lacks of type information for '''return type''', and also we '''don't''' really need it.
 *
 * However, the assembled stream should be cast back to normal type after evaluation at the call side or composition point.
 */
private[machines] class UnsafeFacade[F[_]: Sync] {

  import UnsafeFacade._

  type UnsafeMachine = Machine[F, _, _]

  private val handler = new SignalHandler[F]

  def handlePacket(packet: Packet): Stream[F, OutGoing] =
    packet match {
      case s: Signal => handleSignal(s).map(_ => OutGoing.Ok)
      case Event(to, s: Stream[F, _]) => assemble(s, to).map(s => OutGoing.ReturnVal(s))
      case _ => throw new IllegalAccessError("OutGoing packet should not be evaluated in assembler.")
    }

  private def handleSignal(signal: Signal): Stream[F, Unit] = Stream.eval(handler.handle(signal))

  /**
   * The assemble is called when stream of specific channel is arrived.
   */
  private def assemble(stream: Stream[F, _], channel: UnsafeChannel): Stream[F, _] =
    for {
      o <- Stream.eval(handler.lookUp(channel))
      m <- try Stream.emit(o.get)
      catch {
        case _: Throwable => Stream.raiseError(MachineNotFoundException(s"Not found machine with channel $channel"))
      }
      s <- m.asInstanceOf[Machine[F, Any, _]].evaluateToStream(stream)
    } yield s
}

private[machines] object UnsafeFacade {

  def apply[F[_]: Sync](): UnsafeFacade[F] = new UnsafeFacade()

  type UnsafeChannel = Channel[_]

  sealed trait Packet
  sealed trait Signal extends Packet
  sealed trait OutGoing extends Packet

  // Main data event abstraction.
  case class Event[F[_], T](to: Channel[T], events: Stream[F, T]) extends Packet

  object Signal {
    case class Spawn[F[_], -I, +O](machine: FlyingMachine[F, I, O]) extends Signal
    case class Down(channel: Channel[_]) extends Signal
  }

  object OutGoing {
    case object Ok extends OutGoing
    case class ReturnVal(value: Any) extends OutGoing
  }
}

private[machines] class SignalHandler[F[_]: Sync] {

  import UnsafeFacade._
  import Signal._

  type UnsafeMachine = Machine[F, _, _]

  private val table = TrieMap[UnsafeChannel, UnsafeMachine]()

  private def spawn(channel: UnsafeChannel, machine: UnsafeMachine): F[Unit] =
    Sync[F].delay(table += channel -> machine)

  private def down(channel: UnsafeChannel): F[Unit] =
    Sync[F].delay(table -= channel)

  def lookUp(channel: Channel[_]): F[Option[UnsafeMachine]] =
    Sync[F].delay(table.get(channel))

  def handle(signal: Signal): F[Unit] =
    signal match {
      case s: Spawn[F, _, _] => spawn(s.machine.channel, s.machine)
      case Down(c) => down(c)
    }
}
