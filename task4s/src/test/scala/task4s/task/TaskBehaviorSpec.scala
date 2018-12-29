package task4s.task

import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import task4s.task.TaskBehavior.Eval

import scala.concurrent.Await
import scala.concurrent.duration._

class TaskBehaviorSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit val stage = TaskStage("TestTaskStage")

  override def afterAll(): Unit = Await.result(stage.terminate(), 3.seconds)

  val DummyShape = (_: TaskStage) => { Source(0 to 10).to(Sink.ignore) }

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
