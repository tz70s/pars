package task4s.task

import java.util.UUID.randomUUID

import akka.NotUsed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import task4s.task.TaskStage.TaskStageProtocol
import task4s.task.shape.TaskShape.ShapeBuilder

import scala.concurrent.Future
import scala.concurrent.duration._

abstract class Task private[task4s] (val ref: TaskRef, val shape: ShapeBuilder)(implicit val stage: TaskStage)
    extends TaskBehavior {

  val tpe: String

  private implicit val mat = stage.materializer

  /**
   * The initialization of shape will be constructed here,
   * therefore, any value used in task will be initialized once task factory method being get called.
   */
  private val runnable = shape(stage)

  /**
   * Evaluate internal runnable graph, but no materialized value get returned.
   */
  private[task4s] def eval(): NotUsed =
    // This is expected as unbounded stream that the materialized value will never return.
    runnable.run()

  override def toString: String = s"task-$tpe-${ref.toString}"
}

/**
 * Reference to a specific task.
 *
 * @param definedValue User defined value which indicates task name.
 */
case class TaskRef private[task4s] (definedValue: Option[String]) {
  private[task4s] val value: String = definedValue.getOrElse(s"default-${randomUUID().toString}")

  override def toString: String = value
}

/**
 * Local task class.
 */
private[task4s] class LocalTask(ref: TaskRef, shape: ShapeBuilder)(implicit stage: TaskStage) extends Task(ref, shape) {
  override val tpe = "local"
  override val behavior: Behavior[TaskBehavior.TaskBehaviorProtocol] = TaskBehavior.pure(this)
}

/**
 * Cluster level task.
 */
private[task4s] class ClusterTask(ref: TaskRef, shape: ShapeBuilder)(implicit stage: TaskStage)
    extends Task(ref, shape) {
  override val tpe = "cluster"
  override val behavior: Behavior[TaskBehavior.TaskBehaviorProtocol] = TaskBehavior.pure(this)
}

object Task {

  implicit val DefaultTaskStageTimeout = Timeout(1500.millis)

  /**
   * Create an arbitrary task by passing shape builder closure.
   */
  def local(name: Option[String] = None)(shape: ShapeBuilder)(implicit stage: TaskStage): TaskRef = {
    val ref = TaskRef(name)
    stage += ref -> new LocalTask(ref, shape)
    ref
  }

  /**
   * Spawn a defined task, same interface for cluster or local task.
   *
   * @param ref Reference of task for spawning.
   */
  def spawn(ref: TaskRef)(implicit stage: TaskStage): Future[NotUsed] = {
    implicit val ec = stage.system.executionContext

    stage.lookUpTask(ref) match {
      case Some(task) => internalStaging(task)
      case None =>
        Future.failed(
          TaskNotFoundError(
            "Task lookup failure via task reference. The potential problem is caused by race condition."
          )
        )
    }
  }

  private def internalStaging(task: Task)(implicit stage: TaskStage): Future[NotUsed] = {
    import TaskStageProtocol._

    // Scheduler is required for ask pattern in akka actor typed api.
    implicit val scheduler = stage.system.scheduler
    implicit val ec = stage.system.executionContext
    val result: Future[TaskStageProtocol] = stage.system ? (askRef => Stage(task, askRef))
    result.flatMap {
      case StageReply(actorRef) =>
        stage :+= task.ref -> actorRef
        Future.successful(NotUsed)
      case _ =>
        Future.failed(TaskStageError("Unexpected message return via guardian actor during spawning tasks."))
    }
  }
}
