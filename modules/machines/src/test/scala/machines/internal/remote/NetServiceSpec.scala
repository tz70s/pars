package machines.internal.remote

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.IO
import fs2.Stream
import fs2.concurrent.SignallingRef
import machines.internal.UnsafeFacade.Event
import machines.internal.UnsafeFacade.OutGoing.ReturnVal
import machines.internal.UnsafeFacade.Signal.Spawn
import machines.internal.{ParEffectImpl, UnsafeFacade}
import machines.internal.remote.tcp.AsyncChannelProvider
import org.scalatest.BeforeAndAfterAll
import machines.{Channel, Machine, MachinesSpec, ParEffect}

class NetServiceSpec extends MachinesSpec with BeforeAndAfterAll {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)
  implicit val mill: ParEffect[IO] = ParEffectImpl(UnsafeFacade())

  override def afterAll(): Unit = {
    acg.shutdownNow()
    super.afterAll()
  }

  "NetService" should {
    "spawn flying machine and collect data back after evaluation" in {
      val source = List(1, 2, 3)
      val expect = List(2, 3, 4)

      val assembler = new UnsafeFacade[IO]
      val service = NetService[IO].bindAndHandle(assembler)

      val channel = Channel[Int]("TestChannel")

      val m = Machine.concat(channel) { s: Stream[IO, Int] =>
        for {
          i <- s
          _ <- Stream.eval(IO { println(s"Machine get the number -> $i") })
          u <- Stream.emit(i + 1)
        } yield u
      }

      val peer = NetService.address
      val commands = Stream(Spawn(m), Event(channel, Stream.emits(source)))

      val packets = for {
        signal <- Stream.eval(SignallingRef[IO, Boolean](false))
        packet <- NetService[IO].writeN(peer, commands, signal).take(4).onFinalize(signal.set(true))
      } yield packet

      val response =
        packets.filter(p => p.isInstanceOf[ReturnVal]).map(p => p.asInstanceOf[ReturnVal].value.asInstanceOf[Int])

      val result = (response concurrently service).compile.toList.unsafeRunSync()

      result shouldBe expect
    }
  }
}
