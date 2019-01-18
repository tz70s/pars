package task4s.internal.remote

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.IO
import fs2.Stream
import fs2.concurrent.SignallingRef
import org.scalatest.BeforeAndAfterAll
import task4s.{Channel, Forge, Machine, Task4sSpec}
import task4s.internal.{ForgeImpl, UnsafeFacade}
import task4s.internal.UnsafeFacade.Event.Send
import task4s.internal.UnsafeFacade.OutGoing.ReturnVal
import task4s.internal.UnsafeFacade.Signal.Spawn
import task4s.internal.remote.tcp.AsyncChannelProvider

class NetServiceSpec extends Task4sSpec with BeforeAndAfterAll {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)
  implicit val mill: Forge[IO] = ForgeImpl(UnsafeFacade())

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
      val commands = Stream(Spawn(m), Send(channel, Stream.emits(source)))

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
