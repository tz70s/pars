package task4s.task

import akka.NotUsed
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import org.scalatest.Matchers
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Millis, Span}

import scala.concurrent.Future

object TestDoubles {

  val empty: TaskStage => RunnableGraph[NotUsed] = (_: TaskStage) => Source.empty.to(Sink.ignore)

  val sum: TaskStage => RunnableGraph[Future[Int]] = (_: TaskStage) =>
    Source(0 to 10).toMat(Sink.reduce[Int](_ + _))(Keep.right)
}

class TaskSpec extends TaskStageExtension with Matchers {

  implicit val scale: Span = scaled(Span(300, Millis))

  "Task" should {
    "construct task reference value via factory method" in {
      val localT = Task.local("DummyTask0")(TestDoubles.empty)
      localT.ref shouldBe "DummyTask0"
    }

    "reflect for task type" in {
      val localT = Task.local("DummyTask1")(TestDoubles.empty)
      localT.tpe shouldBe "LocalTask"
    }

    "return materialized value after task spawned" in {
      val localT = Task.local("DummyTask2")(TestDoubles.sum)

      // TODO: the boilerplate flatMap will be removed after revising future.
      val future = Task.spawn(localT).unsafeToFuture().flatMap(f => f)
      future.futureValue(Timeout(scale)) shouldBe 55
    }
  }
}
