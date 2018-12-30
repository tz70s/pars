package task4s.task

import akka.stream.scaladsl.{Keep, Sink, Source}
import org.scalatest.Matchers

object TestDoubles {

  val empty = (stage: TaskStage) => Source.empty.to(Sink.ignore)

  val sum = (stage: TaskStage) => Source(0 to 10).toMat(Sink.reduce[Int](_ + _))(Keep.right)
}

class TaskSpec extends TaskStageExtension with Matchers {

  "Task" should {
    "construct task reference value via factory method" in {
      val localT = Task.local("DummyTask")(TestDoubles.empty)
      localT.ref shouldBe "DummyTask"
    }

    "reflect for task type" in {
      val localT = Task.local(TestDoubles.empty)
      localT.tpe shouldBe "LocalTask"
    }

    "return materialized value after task spawned" in {
      val localT = Task.local(TestDoubles.sum)

      // TODO: the boilerplate flatMap will be removed after revising future.
      val future = Task.spawn(localT).flatMap(f => f)
      future.futureValue shouldBe 55
    }
  }
}
