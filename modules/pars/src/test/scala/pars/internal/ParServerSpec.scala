package pars.internal

import cats.effect.IO
import pars._
import pars.internal.remote.NetService
import org.scalatest.{BeforeAndAfterAll, Matchers}
import fs2.Stream
import pars.cluster.CoordinationProtocol.{AllocationCommand, CommandOk}

class ParServerSpec extends NetParsSpec with Matchers with BeforeAndAfterAll {

  implicit val pe: ParEffect[IO] = ParEffect[IO].localAndOmitCoordinator

  "ParServer" should {

    "spawn pars and ensure allocation correctness" in {
      import Protocol._

      val source = List(1, 2, 3)

      val parServer = ParServer.bindAndHandle[IO](Seq(StandAloneCoordinatorAddress))

      val peer = NetService.address

      val commands = Stream(AllocationCommand(TestPars.toUnsafe, Seq(peer)), Event(TestChannel, Stream.emits(source)))

      val writes = NetService[IO].backOffWriteN(peer, commands)

      val result = (writes concurrently parServer).compile.toList.unsafeRunSync()

      result shouldBe List(CommandOk(TestChannel))
    }

  }
}
