package machines.internal

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.IO
import machines.{Channel, Machine, MachinesSpec, ParEffect}
import machines.internal.remote.NetService
import machines.internal.remote.tcp.AsyncChannelProvider
import org.scalatest.{BeforeAndAfterAll, Matchers}
import fs2.Stream
import fs2.concurrent.SignallingRef
import machines.cluster.CoordinationProtocol.{AllocationCommand, CommandOk}

class ProtocolFSpec extends MachinesSpec with Matchers with BeforeAndAfterAll {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)

  implicit val parEffect: ParEffect[IO] = ParEffect.localAndOmitChannel()

  val channel = Channel[Int]("TestChannel")

  val machine = Machine.concat(channel) { s: Stream[IO, Int] =>
    for {
      i <- s
      _ <- Stream.eval(IO { println(s"Machine get the number -> $i") })
      u <- Stream.emit(i + 1)
    } yield u
  }

  override def afterAll(): Unit = {
    acg.shutdownNow()
    super.afterAll()
  }

  "ProtocolF" should {

    "allocate flying machine and collect data back after evaluation" in {
      import Protocol._

      val source = List(1, 2, 3)

      val protocolF = ProtocolF.bindAndHandle[IO](Seq.empty)

      val peer = NetService.address

      val commands = Stream(AllocationCommand(machine), Event(channel, Stream.emits(source)))

      val packets = for {
        signal <- Stream.eval(SignallingRef[IO, Boolean](false))
        packet <- NetService[IO].writeN(peer, commands, signal).take(4).onFinalize(signal.set(true))
      } yield packet

      val result = (packets concurrently protocolF).compile.toList.unsafeRunSync()

      result shouldBe List(CommandOk(channel), EventOk(2), EventOk(3), EventOk(4))
    }
  }
}
