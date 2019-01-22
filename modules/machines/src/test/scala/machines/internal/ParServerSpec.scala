package machines.internal

import cats.effect.IO
import machines._
import machines.internal.remote.NetService
import org.scalatest.{BeforeAndAfterAll, Matchers}
import fs2.Stream
import machines.cluster.CoordinationProtocol.{AllocationCommand, CommandOk}

class ParServerSpec extends NetMachinesSpec with Matchers with BeforeAndAfterAll with MachinesTestDoubles {

  "ParServer" should {

    "allocate flying TestMachine and collect data back after evaluation" in {
      import Protocol._

      val source = List(1, 2, 3)

      val parServer = ParServer.bindAndHandle[IO](Seq(StandAloneCoordinatorAddress))

      val peer = NetService.address

      val commands = Stream(AllocationCommand(TestMachine), Event(TestChannel, Stream.emits(source)))

      val packets = NetService[IO].writeN(peer, commands)

      val result = (packets concurrently parServer).compile.toList.unsafeRunSync()

      result shouldBe List(CommandOk(TestChannel), EventOk(2), EventOk(3), EventOk(4))
    }

  }
}
