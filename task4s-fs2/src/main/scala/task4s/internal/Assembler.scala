package task4s.internal

import cats.effect.Sync
import fs2.Stream
import MachineError.MachineNotFoundException
import task4s._

import scala.collection.concurrent.TrieMap

/**
 * The assembler '''assemble''' machine to stream.
 * Note that this is a '''DARK SIDE''' behavior which contains unavoidable ''Any'' cast and required high encapsulation.
 *
 * The assembled stream should be whatever cast back to normal type.
 */
private[task4s] class Assembler[F[_]: Sync] {

  import Assembler._
  import Event._

  type UnsafeMachine = Machine[F, _, _]

  private val handler = new SignalHandler[F]

  def handleSignal(signal: Signal): F[Unit] = handler.handle(signal)

  def handleEvent(event: Event): Stream[F, _] =
    event match {
      case Send(to, s: Stream[F, _]) => assemble(s, to)
    }

  /**
   * The assemble is called when stream of specific channel is arrived.
   */
  def assemble(stream: Stream[F, _], channel: UnsafeChannel): Stream[F, _] =
    for {
      o <- Stream.eval(handler.lookUp(channel))
      m <- try Stream.emit(o.get)
      catch {
        case _: Throwable => Stream.raiseError(MachineNotFoundException(s"Not found machine with channel $channel"))
      }
      s <- m.asInstanceOf[Machine[F, Any, _]].assemble(stream)
    } yield s
}

private[task4s] object Assembler {
  type UnsafeChannel = Channel[_]

  sealed trait Command
  sealed trait Event extends Command
  sealed trait Signal extends Command

  object Event {
    case class Send[F[_], T](to: Channel[_], event: Stream[F, T]) extends Event
  }

  object Signal {
    case class Spawn[F[_], -I, +O](machine: FlyingMachine[F, I, O]) extends Signal
    case class Down(channel: Channel[_]) extends Signal
  }
}

private[task4s] class SignalHandler[F[_]: Sync] {

  import Assembler._
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
