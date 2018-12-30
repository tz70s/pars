package task4s.task

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, WordSpec}

class TaskStageExtension extends WordSpec with BeforeAndAfterAll with ScalaFutures {

  implicit val stage = TaskStage("TestTaskStage")
  implicit val ec = stage.system.executionContext

  override def afterAll(): Unit = stage.terminate().futureValue
}
