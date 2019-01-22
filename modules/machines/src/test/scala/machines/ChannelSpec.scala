package machines

import cats.effect.IO
import fs2.Stream
import machines.cluster.internal.StandAloneCoordinator
import machines.internal.ParServer

import cats.implicits._

class ChannelSpec extends NetMachinesSpec with MachinesTestDoubles {

  "Channel" should {

    "work with pub method" in {
      val coordinator = StandAloneCoordinator[IO].bindAndHandle(StandAloneCoordinatorAddress)

      val parServer = new ParServer[IO](Seq(StandAloneCoordinatorAddress))

      val background = Stream(parServer.bindAndHandle, coordinator).parJoin(2)
      val run = parServer.allocate(TestMachine, Strategy(1)) concurrently background

      val source = Stream(1, 2, 3, 4, 5)

      implicit val parEffect = new ParEffect[IO] {
        override val server: ParServer[IO] = parServer
      }

      val pub = TestChannel.pub[IO, Int](source)

      (run *> pub).compile.drain.unsafeRunSync()
    }
  }
}
