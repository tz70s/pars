package task4s.task

import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{Matchers, WordSpec}
import task4s.task.TaskBehavior.Eval

class TaskBehaviorSpec extends WordSpec with Matchers {

  implicit val stage = TaskStage("TestTaskStage")

  val DummyShape = (_: TaskStage) => { Source(0 to 10).to(Sink.foreach(_ => _)) }

  "Task Behavior" should {
    "evaluate internal shape" in {
      val localT = Task.local(Some("DummyTask"))(DummyShape)
      val task = stage.lookUpTask(localT).get
      val testkit = BehaviorTestKit(task.behavior)

      testkit.run(Eval)
      // No effect for visible check currently.
    }
  }
}
