package task4s.task

import java.util.UUID.randomUUID

import akka.NotUsed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.scaladsl.RunnableGraph
import akka.util.Timeout
import task4s.task.Task.{GracefulShutdownHook, ShapeBuilder}
import task4s.task.TaskStage.TaskStageProtocol

import scala.concurrent.Future
import scala.concurrent.duration._

abstract class Task private[task4s] (val ref: TaskRef, val shape: ShapeBuilder, val hook: GracefulShutdownHook)(
    implicit val stage: TaskStage
) extends TaskBehavior {

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

  private[task4s] def shutdown(): Unit = hook()

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
private[task4s] class LocalTask(ref: TaskRef, shape: ShapeBuilder, hook: GracefulShutdownHook)(
    implicit stage: TaskStage
) extends Task(ref, shape, hook) {
  override val tpe = "local"

  override val behavior: Behavior[TaskBehavior.TaskControlProtocol] = TaskBehavior.pure(this)
}

/**
 * Cluster level task.
 */
private[task4s] class ClusterTask(ref: TaskRef, shape: ShapeBuilder, hook: GracefulShutdownHook)(
    implicit stage: TaskStage
) extends Task(ref, shape, hook) {
  override val tpe = "cluster"

  override val behavior: Behavior[TaskBehavior.TaskControlProtocol] = TaskBehavior.pure(this)
}

object Task {

  implicit val DefaultTaskStageTimeout = Timeout(1500.millis)

  private[task4s] type ShapeBuilder = TaskStage => RunnableGraph[NotUsed]

  /**
   * Shutdown hook closure register for manually clean up resources, if any.
   */
  private[task4s] type GracefulShutdownHook = () => Unit

  /**
   * Create an arbitrary task by passing shape builder closure.
   */
  def local(
      name: Option[String] = None,
      hook: GracefulShutdownHook = () => {}
  )(shape: ShapeBuilder)(implicit stage: TaskStage): TaskRef = {
    val ref = TaskRef(name)
    stage.insertTask(ref, new LocalTask(ref, shape, hook))
    ref
  }

  /**
   * Spawn a defined task, same interface for cluster or local task.
   *
   * @param ref Reference of task for spawning.
   */
  def spawn(ref: TaskRef)(implicit stage: TaskStage): Future[NotUsed] = {
    implicit val ec = stage.executionContext

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

  import TaskStageProtocol._

  private def internalStaging(task: Task)(implicit stage: TaskStage): Future[NotUsed] = {
    // Scheduler is required for ask pattern in akka actor typed api.
    implicit val scheduler = stage.system.scheduler
    implicit val ec = stage.executionContext
    val result: Future[TaskStageProtocol] = stage.system ? (askRef => Stage(task, askRef))
    result.flatMap {
      case StageReply(actorRef) =>
        stage.insertActorRef(task.ref, actorRef)
        Future.successful(NotUsed)
      case _ =>
        Future.failed(TaskStageError("Unexpected message return via guardian actor during spawning tasks."))
    }
  }
}
