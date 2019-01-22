package pars.cluster

import cats.effect.IO
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import pars.cluster.CoordinationProtocol.{AllocationCommand, CommandOk, RemovalCommand}
import pars.cluster.internal.StandAloneCoordinator
import pars.internal.{ChannelRouteEntry, ChannelRoutingTable, ParsNotFoundException}
import pars._
import pars.internal.remote.tcp.TcpSocketConfig
import org.scalatest.Matchers
import pars.internal._

import scala.concurrent.duration._

class CoordinatorProxySpec extends NetParsSpec with Matchers {

  implicit val log: Logger[IO] = Slf4jLogger.unsafeCreate[IO]

  implicit val pe: ParEffect[IO] = ParEffect[IO].localAndOmitCoordinator

  "CoordinatorProxy and ChannelRoutingTable" should {

    "work with pars creation locally based on coordinator protocol" in {

      val testPars = TestPars.toUnsafe

      val repository = new ChannelRoutingTable[IO]

      val proxy = CoordinatorProxy[IO](Seq.empty, repository)

      val fakeWorkers = Seq(TcpSocketConfig("localhost", 1234))

      val result = for {
        response <- proxy.handle(AllocationCommand(testPars, fakeWorkers))
        lookUp <- repository.lookUp(TestChannel)
      } yield (response, lookUp)

      val (res, lookUp) = result.compile.toList.unsafeRunSync().head

      res shouldBe CommandOk(TestChannel)
      lookUp shouldBe ChannelRouteEntry(testPars, fakeWorkers)
    }

    "work with pars deletion locally based on coordinator protocol" in {
      val repository = new ChannelRoutingTable[IO]

      val proxy = CoordinatorProxy[IO](Seq.empty, repository)

      val fakeWorkers = Seq(TcpSocketConfig("localhost", 1234))

      val result = for {
        _ <- proxy.handle(AllocationCommand(TestPars.toUnsafe, fakeWorkers))
        _ <- repository.lookUp(TestChannel)
        _ <- proxy.handle(RemovalCommand(TestChannel))
        notExist <- repository.lookUp(TestChannel)
      } yield notExist

      a[ParsNotFoundException] should be thrownBy result.compile.toList.unsafeRunSync().head
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
