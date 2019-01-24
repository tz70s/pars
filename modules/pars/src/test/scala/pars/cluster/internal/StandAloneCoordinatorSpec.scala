package pars.cluster.internal

import cats.effect.{IO, Timer}
import pars._
import pars.internal.remote.tcp.TcpSocketConfig
import org.scalatest.Matchers
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import pars.cluster.CoordinationProtocol._
import pars.internal.ParServer
import pars.internal.remote.NetService
import pars.internal.Protocol.Protocol
import pars.internal._

import scala.concurrent.duration._

class StandAloneCoordinatorSpec extends NetParsSpec with Matchers {

  // First, unit test with manual checking stand alone coordinator behavior.

  implicit val log: Logger[IO] = Slf4jLogger.unsafeCreate[IO]
  implicit val pe: ParEffect[IO] = ParEffect[IO].localAndOmitCoordinator

  "StandAloneCoordinator" should {

    "accept heart beat event" in {
      val coordinator = StandAloneCoordinator[IO](StandAloneCoordinatorAddress).bindAndHandle()

      def heartbeat: Stream[IO, Protocol] =
        NetService[IO]
          .backOffWriteN(StandAloneCoordinatorAddress, Stream.emit(Ping(TcpSocketConfig("localhost", 5678))))

      val run = heartbeat.concurrently(coordinator)

      run.compile.toList.unsafeRunSync().head shouldBe Pong
    }

    "return error cause no worker involved" in {
      val coordinator = StandAloneCoordinator[IO](StandAloneCoordinatorAddress).bindAndHandle()

      def request: Stream[IO, Protocol] =
        NetService[IO]
          .backOffWriteN(StandAloneCoordinatorAddress, Stream.emit(AllocationRequest(TestPars.toUnsafe)))

      val run = request concurrently coordinator
      val err = run.compile.toList.unsafeRunSync().head

      err shouldBe a[RequestErr]
      err.asInstanceOf[RequestErr].throwable shouldBe a[NoAvailableWorker]
    }

    "intercept error cause while fake worker connection failed" in {
      val coordinator = StandAloneCoordinator[IO](StandAloneCoordinatorAddress).bindAndHandle()

      def request: Stream[IO, Protocol] =
        NetService[IO]
          .backOffWriteN(StandAloneCoordinatorAddress,
                         Stream(Ping(TcpSocketConfig("localhost", 7856)), AllocationRequest(TestPars.toUnsafe)))

      val run = request concurrently coordinator
      val rets = run.compile.toList.unsafeRunSync()

      rets.head shouldBe Pong
      rets.tail.head shouldBe a[RequestErr]
    }

    "spawn and accept a successful response" in {
      val coordinator = StandAloneCoordinator[IO](StandAloneCoordinatorAddress).bindAndHandle()

      val server = new ParServer[IO](Seq(StandAloneCoordinatorAddress))

      val background = Stream(server.bindAndHandle, coordinator).parJoin(2)
      val run = server.spawn(TestPars) concurrently background

      val res = run.compile.toList.unsafeRunSync()

      IO.race(Timer[IO].sleep(5.seconds), run.compile.toList).unsafeRunSync().map(_.head) shouldBe Right(TestChannel)
    }
  }
}
