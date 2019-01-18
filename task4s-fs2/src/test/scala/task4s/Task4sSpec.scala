package task4s

import cats.effect.IO
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.global

trait Task4sSpec extends WordSpecLike with Matchers {
  implicit val cs = IO.contextShift(global)
  implicit val timer = IO.timer(global)
}
