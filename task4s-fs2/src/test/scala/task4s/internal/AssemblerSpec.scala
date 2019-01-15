package task4s.internal

import cats.effect.IO
import fs2.Stream
import org.scalatest.{Matchers, WordSpec}
import MachineError.MachineNotFoundException
import task4s.internal.Assembler.Event
import task4s.internal.Assembler.Signal.Spawn
import task4s.{Channel, Machine}

class AssemblerSpec extends WordSpec with Matchers {

  "Assembler and SignalHandler" should {
    "work with crud messages and reflect type correctly" in {

      val source = List(1, 2, 3)
      val expect = source.map(_ + 1)

      val assembler = new Assembler[IO]

      val channel = Channel[Int]("TestChannel")

      // FIXME - should eliminate this type annotation.
      val m = Machine.concat(channel) { s: Stream[IO, Int] =>
        for {
          i <- s
          _ <- Stream.eval(IO { println(i) })
          u <- Stream.emit(i + 1)
        } yield u
      }

      val result = for {
        _ <- Stream.eval(assembler.handleSignal(Spawn(m)))
        s <- assembler.handleEvent(Event.Send(channel, Stream.emits(source)))

        // After assembling, we'll require a manual cast.
        // Hence, we need an additional method to encapsulate this behavior.
        i = s.asInstanceOf[Int]
      } yield i

      result.compile.toList.unsafeRunSync() shouldBe expect
    }

    "intercept assemble failure" in {
      val channel = Channel[Int]("TestChannel")

      val assembler = new Assembler[IO]

      val result = for {
        s <- assembler.assemble(Stream.emit(1), channel)
        i = s.asInstanceOf[Int]
      } yield i

      an[MachineNotFoundException] should be thrownBy result.compile.toList.unsafeRunSync()
    }
  }
}
