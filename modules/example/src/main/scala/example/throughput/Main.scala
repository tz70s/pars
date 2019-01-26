package example.throughput
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.TimeUnit

import cats.effect.{ContextShift, IO, Timer}
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import pars.cluster.internal.StandAloneCoordinator
import pars.{Channel, ParEffect, Pars, ParsM}
import pars.internal.remote.tcp.{AsyncChannelProvider, TcpSocketConfig}
import cats.implicits._

object Main {
  private val mapperIn = Channel[Int]("mapper-in")
  private val mapperOut = Channel[Int]("mapper-out")

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  implicit val log: Logger[IO] = Slf4jLogger.unsafeCreate[IO]

  val CoordinatorAddress = TcpSocketConfig("localhost", 9981)

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance()

  implicit val pe: ParEffect[IO] = ParEffect[IO].bindCoordinator(CoordinatorAddress)

  def source(implicit pe: ParEffect[IO]): ParsM[IO, Int] =
    Pars.emits(mapperIn)(0 to 100000)

  def mapper(implicit pe: ParEffect[IO]): Pars[IO, Int, Int] = Pars.concat(mapperIn, mapperOut) { from =>
    for {
      i <- from
      plusOne = i + 1
    } yield plusOne
  }

  def run(implicit pe: ParEffect[IO], cs: ContextShift[IO], timer: Timer[IO]): Stream[IO, Unit] =
    for {
      c <- Pars.spawn(source).concurrently(Pars.spawn(mapper))
      t1 <- Stream.eval(timer.clock.realTime(TimeUnit.MILLISECONDS))
      _ <- mapperOut
        .receive[IO]
        .concurrently(c.replayUnit())
        .onFinalize {
          timer.clock.realTime(TimeUnit.MILLISECONDS).map(l => l - t1).flatMap { l =>
            IO { println(s"Duration: $l millis") }
          }
        }
    } yield ()

  def spawnAndRun(): Unit =
    Pars.service(run)(pe)(StandAloneCoordinator[IO](CoordinatorAddress)).compile.drain.unsafeRunSync()

  def main(args: Array[String]): Unit =
    spawnAndRun()
}
