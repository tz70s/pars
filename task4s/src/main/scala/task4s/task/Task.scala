package task4s.task

import java.util.UUID.randomUUID

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import cats.effect.IO
import task4s.task.TaskProtocol.TaskProtocol
import task4s.task.TaskStage.TaskStageProtocol
import task4s.task.par.ParStrategy
import task4s.task.shape.TaskShape.ShapeBuilder

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

abstract class Task[+Mat] private[task4s] (val ref: String, val shape: ShapeBuilder[Mat])(
    implicit val stage: TaskStage
) extends TaskBehavior
    with Serializable {

  def tpe: String = this.getClass.getCanonicalName.split("\\.").last

  private implicit val mat = stage.materializer

  /**
   * The initialization of shape will be constructed here,
   * therefore, any value used in task will be initialized once task factory method being get called.
   */
  private val runnable = shape(stage)

  // Initialize section.
  {
    stage.tasks += (ref -> this)
  }

  /**
   * Materialized internal runnable graph, but no materialized value get returned.
   */
  private[task4s] def spawn(): Mat =
    // This is expected as unbounded stream that the materialized value will never return.
    runnable.run()

  override def toString: String = s"task-$tpe-$ref}"

  override def equals(obj: Any): Boolean =
    obj match {
      case t: Task[Mat] =>
        ref.equals(t.ref)
      case _ =>
        false
    }

  override def hashCode(): Int = ref.hashCode
}

/**
 * Local task class.
 */
private[task4s] class LocalTask[+Mat](ref: String, shape: ShapeBuilder[Mat])(implicit stage: TaskStage)
    extends Task(ref, shape)

/**
 * Cluster level task.
 */
private[task4s] class ClusterTask[+Mat](ref: String, shape: ShapeBuilder[Mat], val strategy: ParStrategy)(
    implicit stage: TaskStage
) extends Task(ref, shape) {

  override val behavior: Behavior[TaskProtocol] = pure
}

object Task {

  implicit val DefaultTaskSpawnTimeout = Timeout(1500.millis)

  private def autoRef: String = s"default-${randomUUID().toString}"

  /**
   * Create a local task by passing shape builder closure.
   *
   * @param name Task reference name.
   * @param shape Task data flow shape builder closure.
   * @param stage Task stage for spawning tasks internally.
   * @tparam M Materialized value of shape. (see akka stream)
   * @return Task instance.
   */
  def local[M](name: String)(shape: ShapeBuilder[M])(implicit stage: TaskStage): Task[M] =
    new LocalTask(name, shape)

  /**
   * Create a local task by passing shape builder closure.
   *
   * @param shape Task data flow shape builder closure.
   * @param stage Task stage for spawning tasks internally.
   * @tparam M Materialized value of shape. (see akka stream)
   * @return Task instance.
   */
  def local[M](shape: ShapeBuilder[M])(implicit stage: TaskStage): Task[M] =
    local(autoRef)(shape)

  /**
   * Create a cluster task by passing shape builder closure.
   *
   * @param strategy ParStrategy, indicates the parallel settings of cluster aware task allocation.
   * @param name Task reference name.
   * @param shape Task data flow shape builder closure.
   * @param stage Task stage for spawning tasks internally.
   * @tparam M Materialized value of shape. (see akka stream)
   * @return Task instance.
   */
  def cluster[M](strategy: ParStrategy, name: String)(shape: ShapeBuilder[M])(implicit stage: TaskStage): Task[M] =
    new ClusterTask[M](name, shape, strategy)

  /**
   * Create a cluster task by passing shape builder closure.
   *
   * @param strategy ParStrategy, indicates the parallel settings of cluster aware task allocation.
   * @param shape Task data flow shape builder closure.
   * @param stage Task stage for spawning tasks internally.
   * @tparam M Materialized value of shape. (see akka stream)
   * @return Task instance.
   */
  def cluster[M](strategy: ParStrategy)(shape: ShapeBuilder[M])(implicit stage: TaskStage): Task[M] =
    cluster(strategy, autoRef)(shape)

  /**
   * Create a cluster task by passing shape builder closure.
   *
   * The ParStrategy use the default strategy [[par.ParStrategy.DefaultParStrategy]].
   *
   * @param shape Task data flow shape builder closure.
   * @param stage Task stage for spawning tasks internally.
   * @tparam M Materialized value of shape. (see akka stream)
   * @return Task instance.
   */
  def cluster[M](name: String)(shape: ShapeBuilder[M])(implicit stage: TaskStage): Task[M] =
    cluster(ParStrategy.DefaultParStrategy, name)(shape)

  /**
   * Create a cluster task by passing shape builder closure.
   *
   * The ParStrategy use the default strategy [[par.ParStrategy.DefaultParStrategy]].
   *
   * @param shape Task data flow shape builder closure.
   * @param stage Task stage for spawning tasks internally.
   * @tparam M Materialized value of shape. (see akka stream)
   * @return Task instance.
   */
  def cluster[M](shape: ShapeBuilder[M])(implicit stage: TaskStage): Task[M] =
    cluster(ParStrategy.DefaultParStrategy, autoRef)(shape)

  /**
   * Spawn a defined task, same interface for cluster or local task.
   *
   * @param task Task for spawning.
   * @param stage Task stage for spawning tasks internally.
   * @tparam M Materialized value of shape. (see akka stream)
   * @return Materialized future value.
   */
  def spawn[M: ClassTag](task: Task[M])(implicit stage: TaskStage): IO[M] = {
    import TaskStageProtocol._
    implicit val ec = stage.system.executionContext

    val futureVal = task match {
      case l: LocalTask[M] => spawnLocal(l)
      case c: ClusterTask[M] => spawnCluster(c)
    }

    IO.async { callback =>
      futureVal.onComplete {
        case Success(StageSuccess(_, matValue: M, _)) =>
          callback(Right(matValue))
        case Success(StageFailure(cause, _)) =>
          callback(Left(cause))
        case Success(msg) =>
          callback(Left(TaskStageError(s"Unexpected message return via guardian actor during spawning tasks. $msg")))
        case Failure(cause) =>
          callback(Left(cause))
      }
    }
  }

  private def spawnLocal[M: ClassTag](task: LocalTask[M])(implicit stage: TaskStage): Future[TaskStageProtocol] = {
    import TaskStageProtocol._

    implicit val ec = stage.system.executionContext
    // Scheduler is required for ask pattern in akka actor typed API.
    implicit val scheduler = stage.system.scheduler
    stage.system ? (askRef => LocalStage(task, askRef))
  }

  private def spawnCluster[M: ClassTag](task: ClusterTask[M])(implicit stage: TaskStage): Future[TaskStageProtocol] = {
    import TaskStageProtocol._

    implicit val ec = stage.system.executionContext
    // Scheduler is required for ask pattern in akka actor typed API.
    implicit val scheduler = stage.system.scheduler
    stage.system ? (askRef => ClusterStage(task, askRef))
  }
}
