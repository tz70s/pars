package task4s.task

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{Matchers, WordSpec}

class TaskSpec extends WordSpec with Matchers {

  implicit val stage = TaskStage("TestTaskStage")

  val DummyShape = (_: TaskStage) => { Source(0 to 10).to(Sink.foreach(_ => _)) }

  "Task Behavior" should {
    "constructed via task factory method" in {
      val localT = Task.local(Some("DummyTask"))(DummyShape)
      // forced lookup
      val task = stage.lookUpTask(localT).get
      task.tpe shouldBe "local"
      task.ref shouldBe localT
    }
  }
}
