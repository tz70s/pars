package machines

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ContextShift, IO, Timer}
import fs2.Stream
import machines.internal.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.global

trait MachinesSpec extends WordSpecLike with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)
}

trait NetMachinesSpec extends MachinesSpec with BeforeAndAfterAll {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)

  override def afterAll(): Unit = {
    acg.shutdownNow()
    super.afterAll()
  }
}

trait MachinesTestDoubles {

  implicit val parEffect: ParEffect[IO] = ParEffect.localAndOmitChannel()

  val StandAloneCoordinatorAddress = TcpSocketConfig("localhost", 9898)

  val TestChannel: Channel[Int] = Channel[Int]("TestChannel")

  val TestMachine: FlyingMachine[IO, Int, Int] = Machine.concat(TestChannel) { s: Stream[IO, Int] =>
    for {
      i <- s
      _ <- Stream.eval(IO { println(s"Machine get the number -> $i") })
      u <- Stream.emit(i + 1)
    } yield u
  }
}
