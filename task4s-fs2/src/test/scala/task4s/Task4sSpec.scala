package task4s

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.global

trait Task4sSpec extends WordSpecLike with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)
}
