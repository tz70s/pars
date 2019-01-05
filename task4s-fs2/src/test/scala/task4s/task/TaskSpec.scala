package task4s.task

import cats.effect.Concurrent
import org.scalatest.{Matchers, WordSpec}
import fs2.Stream

class TaskSpec extends WordSpec with Matchers {

  "Task" should {

    "build shape for running pure stream" in {
      val task = Task[Int, Int]("hello")(s => s.map(_ + 1))
      Stream.range(1, 10).through(task.shape).compile.toList shouldBe Stream.range(2, 11).compile.toList
    }

    "reflect for runtime type tag" in {}
  }
}
