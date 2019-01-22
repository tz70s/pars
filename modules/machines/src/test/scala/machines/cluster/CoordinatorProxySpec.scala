package machines.cluster

import cats.effect.IO
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import machines.cluster.CoordinationProtocol.{AllocationCommand, CommandOk, RemovalCommand}
import machines.cluster.internal.StandAloneCoordinator
import machines.internal.{ChannelRouteEntry, ChannelRoutingTable, MachineNotFoundException}
import machines._
import machines.internal.remote.tcp.TcpSocketConfig
import org.scalatest.Matchers

import scala.concurrent.duration._

class CoordinatorProxySpec extends NetMachinesSpec with Matchers with MachinesTestDoubles {

  implicit val log: Logger[IO] = Slf4jLogger.unsafeCreate[IO]

  "CoordinatorProxy and Machine Repository" should {

    "work with machine creation locally based on coordinator protocol" in {

      val repository = new ChannelRoutingTable[IO]

      val proxy = CoordinatorProxy[IO](Seq.empty, repository)

      val fakeWorkers = Seq(TcpSocketConfig("localhost", 1234))

      val result = for {
        response <- proxy.handle(AllocationCommand(TestMachine, fakeWorkers))
        lookUp <- repository.lookUp(TestChannel)
      } yield (response, lookUp)

      val (res, lookUp) = result.compile.toList.unsafeRunSync().head

      res shouldBe CommandOk(TestChannel)
      lookUp shouldBe ChannelRouteEntry(TestMachine, fakeWorkers)
    }

    "work with machine deletion locally based on coordinator protocol" in {
      val repository = new ChannelRoutingTable[IO]

      val proxy = CoordinatorProxy[IO](Seq.empty, repository)

      val fakeWorkers = Seq(TcpSocketConfig("localhost", 1234))

      val result = for {
        _ <- proxy.handle(AllocationCommand(TestMachine, fakeWorkers))
        _ <- repository.lookUp(TestChannel)
        _ <- proxy.handle(RemovalCommand(TestChannel))
        notExist <- repository.lookUp(TestChannel)
      } yield notExist

      a[MachineNotFoundException] should be thrownBy result.compile.toList.unsafeRunSync().head
    }
  }

  "ConnectionStateManagement" should {
    "work with periodic health check (no assertion in this case)" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      val management = ConnectionStateManagement[IO](Seq(StandAloneCoordinatorAddress))

      val blockUntilConnect = management.blockUntilConnect concurrently management.healthCheck()

      val timeout = timer.sleep(5.second)

      val checker = blockUntilConnect.concurrently(coordinator)
      IO.race(timeout, checker.compile.drain).unsafeRunSync()
    }
  }
}
