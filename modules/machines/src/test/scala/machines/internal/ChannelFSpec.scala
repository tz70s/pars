package machines.internal

import cats.effect.IO
import fs2.Stream
import machines.internal.Protocol.{Event, EventOk}
import machines.{Channel, Machine, MachinesSpec, ParEffect}

class ChannelFSpec extends MachinesSpec {

  implicit val parEffect: ParEffect[IO] = ParEffect.localAndOmitChannel()

  val channel = Channel[Int]("TestChannel")

  // FIXME - should eliminate this type annotation.
  val m = Machine.concat(channel) { s: Stream[IO, Int] =>
    for {
      i <- s
      _ <- Stream.eval(IO { println(i) })
      u <- Stream.emit(i + 1)
    } yield u
  }

  "ChannelF" should {
    "evaluate machine and can be reflected type correctly, manually" in {
      val source = List(1, 2, 3)
      val expect = source.map(_ + 1)

      val repository = new MachineRepository[IO]

      val channelF = new ChannelF[IO](repository)

      val result = for {
        _ <- Stream.eval(repository.allocate(channel, m))
        s <- channelF.handle(Event(channel, Stream.emits(source))).map {
          case EventOk(ret) => ret.asInstanceOf[Int]
          case _ => 0
        }
      } yield s

      result.compile.toList.unsafeRunSync() shouldBe expect
    }

    "intercept assemble failure" in {
      val repository = new MachineRepository[IO]

      val channelF = new ChannelF[IO](repository)

      val result = for {
        s <- channelF.handle(Event(channel, Stream(1)))
        i = s.asInstanceOf[Int]
      } yield i

      an[MachineNotFoundException] should be thrownBy result.compile.toList.unsafeRunSync()
    }
  }
}
