package task4s.task

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.stream.typed.scaladsl.ActorMaterializer
import task4s.task.TaskBehavior.TaskBehaviorProtocol
import task4s.task.par.ClusterExtension

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

/**
 * The execution instance and associated context.
 *
 * @param name Name for task stage namespace, will propagate to underlying [[akka.actor.typed.ActorSystem]] name.
 */
class TaskStage private (val name: String) {
  import TaskStage._

  // Make the task stage thread safe.
  private val tasks = TrieMap[TaskRef, Task]()
  private val activeTasks = TrieMap[TaskRef, ActorRef[TaskBehaviorProtocol]]()

  /**
   * Access internal actor system for flexibility, i.e. spawn custom actor, using dispatcher, etc.
   */
  implicit val system: ActorSystem[TaskStageProtocol] = ActorSystem(guardian(this), name)

  /**
   * Stream materializer factored via default actor system.
   */
  val materializer: ActorMaterializer = ActorMaterializer()(system)

  private[task4s] val clusterExt: ClusterExtension = new ClusterExtension()

  def terminate(): Future[Terminated] = {
    materializer.shutdown()
    system.terminate()
  }

  /**
   * Look up the task via task reference.
   *
   * @param ref Reference of lookup task.
   * @return Optional value of lookup task.
   */
  private[task4s] def lookUpTask(ref: TaskRef): Option[Task] =
    tasks.get(ref)

  /**
   * Insert task with task reference for lookup.
   *
   * @param tuple Task reference and insertion task.
   */
  private[task4s] def +=(tuple: (TaskRef, Task)): Unit =
    tasks += tuple

  /**
   * Lookup actor reference via task reference.
   *
   * @param ref Reference of lookup task.
   * @return Optional value of lookup actor reference.
   */
  private[task4s] def lookUpActorRef(ref: TaskRef): Option[ActorRef[TaskBehaviorProtocol]] = activeTasks.get(ref)

  /**
   * Insert actor reference with task reference for lookup.
   *
   * @param tuple Task reference and insertion actor reference.
   */
  private[task4s] def :+=(tuple: (TaskRef, ActorRef[TaskBehaviorProtocol])): Unit =
    activeTasks += tuple
}

object TaskStage {

  def apply(name: String): TaskStage = new TaskStage(name)

  /**
   * Task stage related protocols.
   */
  private[task4s] sealed trait TaskStageProtocol

  private[task4s] object TaskStageProtocol {
    case object Shutdown extends TaskStageProtocol

    case class Stage(task: Task, respondTo: ActorRef[TaskStageProtocol]) extends TaskStageProtocol
    case class StageReply(ref: ActorRef[TaskBehaviorProtocol]) extends TaskStageProtocol

  }

  import TaskStageProtocol._

  private def guardian(stage: TaskStage): Behavior[TaskStageProtocol] =
    Behaviors.setup(ctx => stageTasks(ctx))

  private def stageTasks(context: ActorContext[TaskStageProtocol]): Behavior[TaskStageProtocol] =
    Behaviors.receiveMessagePartial {
      case Stage(task, respondTo) =>
        val ref = TaskBehavior.spawn(task, context)
        respondTo ! StageReply(ref)
        Behaviors.same
    }
}
