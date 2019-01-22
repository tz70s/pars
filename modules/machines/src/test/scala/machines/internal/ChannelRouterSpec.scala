package machines.internal

import cats.effect.IO
import fs2.Stream
import machines.internal.Protocol.{Event, EventOk}
import machines.{MachinesSpec, MachinesTestDoubles}

class ChannelRouterSpec extends MachinesSpec with MachinesTestDoubles {

  "ChannelRouter" should {
    "evaluate TestMachine and can be reflected type correctly, manually" in {
      val source = List(1, 2, 3)
      val expect = source.map(_ + 1)

      val repository = new MachineRepository[IO]

      val router = new ChannelRouter[IO](repository)

      val result = for {
        _ <- Stream.eval(repository.allocate(TestChannel, TestMachine))
        s <- router.handle(Event(TestChannel, Stream.emits(source))).map {
          case EventOk(ret) => ret.asInstanceOf[Int]
          case _ => 0
        }
      } yield s

      result.compile.toList.unsafeRunSync() shouldBe expect
    }

    "intercept assemble failure" in {
      val repository = new MachineRepository[IO]

      val router = new ChannelRouter[IO](repository)

      val result = for {
        s <- router.handle(Event(TestChannel, Stream(1)))
        i = s.asInstanceOf[Int]
      } yield i

      an[MachineNotFoundException] should be thrownBy result.compile.toList.unsafeRunSync()
    }
  }
}
