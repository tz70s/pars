package machines.cluster

import cats.effect.IO
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import machines.cluster.CoordinationProtocol.{AllocationCommand, CommandOk, RemovalCommand}
import machines.cluster.internal.StandAloneCoordinator
import machines.internal.MachineRepository
import machines._
import org.scalatest.Matchers

import scala.concurrent.duration._

class CoordinatorProxySpec extends NetMachinesSpec with Matchers with MachinesTestDoubles {

  implicit val log: Logger[IO] = Slf4jLogger.unsafeCreate[IO]

  "CoordinatorProxy and Machine Repository" should {

    "work with TestMachine creation locally based on coordinator protocol" in {

      val repository = new MachineRepository[IO]

      val proxy = CoordinatorProxy[IO](Seq.empty, repository)

      val result = for {
        response <- proxy.handle(AllocationCommand(TestMachine))
        lookUp <- Stream.eval(repository.lookUp(TestChannel))
      } yield (response, lookUp)

      val (res, lookUp) = result.compile.toList.unsafeRunSync().head

      res shouldBe CommandOk(TestChannel)
      lookUp shouldBe Some(TestMachine)
    }

    "work with TestMachine deletion locally based on coordinator protocol" in {
      val repository = new MachineRepository[IO]

      val proxy = CoordinatorProxy[IO](Seq.empty, repository)

      val result = for {
        _ <- proxy.handle(AllocationCommand(TestMachine))
        exist <- Stream.eval(repository.lookUp(TestChannel))
        _ <- proxy.handle(RemovalCommand(TestChannel))
        notExist <- Stream.eval(repository.lookUp(TestChannel))
      } yield (exist, notExist)

      val (exist, notExist) = result.compile.toList.unsafeRunSync().head

      exist shouldBe Some(TestMachine)
      notExist shouldBe None
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
