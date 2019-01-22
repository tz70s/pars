package machines.cluster.internal

import cats.effect.IO
import machines._
import machines.internal.remote.tcp.TcpSocketConfig
import org.scalatest.Matchers
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import machines.cluster.CoordinationProtocol._
import machines.internal.ParServer
import machines.internal.remote.NetService
import machines.internal.Protocol.Protocol

class StandAloneCoordinatorSpec extends NetMachinesSpec with Matchers with MachinesTestDoubles {

  // First, unit test with manual checking stand alone coordinator behavior.

  implicit val log: Logger[IO] = Slf4jLogger.unsafeCreate[IO]

  "StandAloneCoordinator" should {

    "accept heart beat event" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      def heartbeat: Stream[IO, Protocol] =
        NetService[IO]
          .backOffWriteN(StandAloneCoordinatorAddress, Stream.emit(Ping(TcpSocketConfig("localhost", 5678))))

      val run = heartbeat.concurrently(coordinator)

      run.compile.toList.unsafeRunSync().head shouldBe Pong
    }

    "return error cause no worker involved" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      def request: Stream[IO, Protocol] =
        NetService[IO]
          .backOffWriteN(StandAloneCoordinatorAddress, Stream.emit(AllocationRequest(TestMachine, Strategy(1))))

      val run = request concurrently coordinator
      val err = run.compile.toList.unsafeRunSync().head

      err shouldBe a[RequestErr]
      err.asInstanceOf[RequestErr].throwable shouldBe a[NoAvailableWorker]
    }

    "intercept error cause while fake worker connection failed" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      def request: Stream[IO, Protocol] =
        NetService[IO]
          .backOffWriteN(StandAloneCoordinatorAddress,
                         Stream(Ping(TcpSocketConfig("localhost", 7856)), AllocationRequest(TestMachine, Strategy(1))))

      val run = request concurrently coordinator
      val rets = run.compile.toList.unsafeRunSync()

      rets.head shouldBe Pong
      rets.tail.head shouldBe a[RequestErr]
    }

    "allocate and accept a successful response" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      val protocolF = new ParServer[IO](Seq(StandAloneCoordinatorAddress))

      val background = Stream(protocolF.bindAndHandle, coordinator).parJoin(2)
      val run = protocolF.allocate(TestMachine, Strategy(1)) concurrently background

      val res = run.compile.toList.unsafeRunSync()

      res.head shouldBe CommandOk(TestChannel)
    }
  }
}
