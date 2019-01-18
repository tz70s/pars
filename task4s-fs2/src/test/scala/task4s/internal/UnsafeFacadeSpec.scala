package task4s.internal

import cats.effect.IO
import fs2.Stream
import task4s.internal.UnsafeFacade.{Event, OutGoing}
import task4s.internal.UnsafeFacade.Signal.Spawn
import task4s.{Channel, Forge, Machine, Task4sSpec}

class UnsafeFacadeSpec extends Task4sSpec {

  implicit val mill: Forge[IO] = ForgeImpl(UnsafeFacade())

  "UnsafeAssembler and SignalHandler" should {
    "work with crud messages and reflect type correctly" in {

      val source = List(1, 2, 3)
      val expect = source.map(_ + 1)

      val facade = new UnsafeFacade[IO]

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
        _ <- facade.eval(Spawn(m))
        s <- facade.eval(Event.Send(channel, Stream.emits(source)))

        // After assembling, we'll require a manual cast.
        // Hence, we need an additional method to encapsulate this behavior.
        i = s match { case OutGoing.ReturnVal(value) => value.asInstanceOf[Int]; case _ => 0 }
      } yield i

      result.compile.toList.unsafeRunSync() shouldBe expect
    }

    "intercept assemble failure" in {
      val channel = Channel[Int]("TestChannel")

      val facade = new UnsafeFacade[IO]

      val result = for {
        s <- facade.eval(Event.Send(channel, Stream(1)))
        i = s.asInstanceOf[Int]
      } yield i

      an[MachineNotFoundException] should be thrownBy result.compile.toList.unsafeRunSync()
    }
  }
}
