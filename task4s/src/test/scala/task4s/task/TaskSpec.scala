package task4s.task

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class TaskSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit val stage = TaskStage("TestTaskStage")

  override def afterAll(): Unit = Await.result(stage.terminate(), 3.seconds)

  val DummyShape = (_: TaskStage) => { Source(0 to 10).to(Sink.ignore) }

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
