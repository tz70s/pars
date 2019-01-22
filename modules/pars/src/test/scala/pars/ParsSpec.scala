package pars

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ContextShift, IO, Timer}
import fs2.Stream
import pars.internal.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.global

trait ParsSpec extends WordSpecLike with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)
}

trait NetParsSpec extends ParsSpec with BeforeAndAfterAll {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)

  override def afterAll(): Unit = {
    acg.shutdownNow()
    super.afterAll()
  }

  val StandAloneCoordinatorAddress = TcpSocketConfig("localhost", 9898)

  def TestChannel(implicit parEffect: ParEffect[IO]): Channel[Int] = Channel[Int]("TestChannel")

  def TestPars(implicit parEffect: ParEffect[IO]): Pars[IO, Int, Int] = Pars.concat(TestChannel) { s: Stream[IO, Int] =>
    for {
      i <- s
      _ <- Stream.eval(IO { println(s"Pars get the number -> $i") })
      u <- Stream.emit(i + 1)
    } yield u
  }
}
