package machines.internal

import cats.effect.IO
import fs2.Stream
import machines.internal.Protocol.{Event, EventOk}
import machines.{MachinesSpec, MachinesTestDoubles}

class ChannelFSpec extends MachinesSpec with MachinesTestDoubles {

  "ChannelF" should {
    "evaluate TestMachine and can be reflected type correctly, manually" in {
      val source = List(1, 2, 3)
      val expect = source.map(_ + 1)

      val repository = new MachineRepository[IO]

      val channelF = new ChannelF[IO](repository)

      val result = for {
        _ <- Stream.eval(repository.allocate(TestChannel, TestMachine))
        s <- channelF.handle(Event(TestChannel, Stream.emits(source))).map {
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
        s <- channelF.handle(Event(TestChannel, Stream(1)))
        i = s.asInstanceOf[Int]
      } yield i

      an[MachineNotFoundException] should be thrownBy result.compile.toList.unsafeRunSync()
    }
  }
}
