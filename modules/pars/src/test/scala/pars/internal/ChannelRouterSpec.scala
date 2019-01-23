package pars.internal

import cats.effect.IO
import fs2.Stream
import pars.internal.Protocol.{Event, EventErr, EventOk}
import pars.internal.remote.tcp.TcpSocketConfig
import pars.{NetParsSpec, ParEffect}

class ChannelRouterSpec extends NetParsSpec {

  implicit val pe: ParEffect[IO] = ParEffect[IO].localAndOmitCoordinator

  "ChannelRouter" should {
    "evaluate TestPars and can be reflected type correctly, manually" in {
      val source = List(1, 2, 3)

      val table = new ChannelRoutingTable[IO]

      val router = new ChannelRouter[IO](table)

      val fakeWorker = Seq(TcpSocketConfig("localhost", 8181))

      val result = for {
        _ <- table.allocate(TestPars.toUnsafe, fakeWorker)
        s <- router.receive(Event(TestChannel, Stream.emits(source)))
      } yield s

      result.compile.toList.unsafeRunSync()
    }

    "intercept pars processing (evaluation) failure" in {
      val repository = new ChannelRoutingTable[IO]

      val router = new ChannelRouter[IO](repository)

      val result = for {
        s <- router.receive(Event(TestChannel, Stream(1)))
      } yield s

      result.compile.toList.unsafeRunSync().head shouldBe a[EventErr]
    }
  }
}
