package example

import cats.effect.{ContextShift, IO, Timer}
import pars.{Channel, ParEffect, Pars, ParsM}
import fs2.Stream

object PlusOne {

  private val mapperIn = Channel[Int]("mapper-in")
  private val mapperOut = Channel[Int]("mapper-out")

  def source(implicit pe: ParEffect[IO]): ParsM[IO, Int] = Pars.emits(mapperIn)(0 to 100)

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
      s <- mapperOut.receive[IO].concurrently(c.unit())
    } yield s
}
