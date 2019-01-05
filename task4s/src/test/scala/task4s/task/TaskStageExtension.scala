package task4s.task

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, WordSpec}

import scala.concurrent.ExecutionContextExecutor

class TaskStageExtension extends WordSpec with BeforeAndAfterAll with ScalaFutures {

  implicit val stage: TaskStage = TaskStage("TestTaskStage")
  implicit val ec: ExecutionContextExecutor = stage.system.executionContext

  override def afterAll(): Unit = stage.terminate().futureValue
}
