package pars.internal

import cats.effect.IO
import fs2.Stream
import pars.internal.Protocol.{Event, EventOk}
import pars.internal.remote.tcp.TcpSocketConfig
import pars.{NetParsSpec, ParEffect}

class ChannelRouterSpec extends NetParsSpec {

  implicit val pe: ParEffect[IO] = ParEffect[IO].localAndOmitCoordinator

  "ChannelRouter" should {
    "evaluate TestPars and can be reflected type correctly, manually" in {
      val source = List(1, 2, 3)
      val expect = source.map(_ + 1)

      val repository = new ChannelRoutingTable[IO]

      val router = new ChannelRouter[IO](repository)

      val fakeWorker = Seq(TcpSocketConfig("localhost", 8181))

      val result = for {
        _ <- repository.allocate(TestPars.toUnsafe, fakeWorker)
        s <- router.receive(Event(TestChannel, Stream.emits(source))).map {
          case EventOk(ret) => ret.asInstanceOf[Int]
          case _ => 0
        }
      } yield s

      result.compile.toList.unsafeRunSync() shouldBe expect
    }

    "intercept assemble failure" in {
      val repository = new ChannelRoutingTable[IO]

      val router = new ChannelRouter[IO](repository)

      val result = for {
        s <- router.receive(Event(TestChannel, Stream(1)))
        i = s.asInstanceOf[Int]
      } yield i

      an[ParsNotFoundException] should be thrownBy result.compile.toList.unsafeRunSync()
    }
  }
}
