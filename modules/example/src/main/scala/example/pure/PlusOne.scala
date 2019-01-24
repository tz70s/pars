package example.pure

import cats.effect.{ContextShift, IO, Timer}
import fs2.Stream
import pars.{Channel, ParEffect, Pars, ParsM}

import scala.concurrent.duration._

object PlusOne {

  private val mapperIn = Channel[Int]("mapper-in")
  private val mapperOut = Channel[Int]("mapper-out")

  def source(implicit pe: ParEffect[IO]): ParsM[IO, Int] =
    Pars.emits(mapperIn)(0 to 10)

  def mapper(implicit pe: ParEffect[IO]): Pars[IO, Int, Int] = Pars.concat(mapperIn, mapperOut) { from =>
    for {
      i <- from
      plusOne = i + 1
      _ <- Stream.eval(IO { println(s"Get the source value $i and map it to $plusOne") })
    } yield plusOne
  }

  def run(implicit pe: ParEffect[IO], cs: ContextShift[IO], timer: Timer[IO]): Stream[IO, Int] =
    for {
      c <- Pars.spawn(source).concurrently(Pars.spawn(mapper))
      _ <- Stream.awakeEvery[IO](1000.millis)
      s <- mapperOut.receive[IO].concurrently(c.replayUnit())
    } yield s
}
