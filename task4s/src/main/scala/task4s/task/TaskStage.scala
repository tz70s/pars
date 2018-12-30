package task4s.task

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.stream.typed.scaladsl.ActorMaterializer
import task4s.task.TaskProtocol.TaskProtocol
import task4s.task.par.ClusterExtension

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
 * The execution instance and associated context.
 *
 * @param name Name for task stage namespace, will propagate to underlying [[akka.actor.typed.ActorSystem]] name.
 */
class TaskStage private (val name: String) {
  import TaskStage._

  // Make the task stage thread safe.
  private val activeTasks = TrieMap[String, ActorRef[TaskProtocol]]()

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
   * Lookup actor reference via task reference.
   *
   * @param task Reference of lookup task.
   * @return Optional value of lookup actor reference.
   */
  private[task4s] def apply[M](task: Task[M]): Option[ActorRef[TaskProtocol]] = activeTasks.get(task.ref)

  /**
   * Insert actor reference with task reference for lookup.
   *
   * @param tuple Task and insertion actor reference.
   */
  private[task4s] def +=[M](tuple: (Task[M], ActorRef[TaskProtocol])): Unit =
    activeTasks += (tuple._1.ref -> tuple._2)
}

object TaskStage {

  def apply(name: String): TaskStage = new TaskStage(name)

  /**
   * Task stage related protocols.
   */
  private[task4s] sealed trait TaskStageProtocol {
    val replyTo: ActorRef[TaskStageProtocol]
  }

  private[task4s] object TaskStageProtocol {

    case class Stage[M: ClassTag](task: Task[M], override val replyTo: ActorRef[TaskStageProtocol])
        extends TaskStageProtocol

    sealed trait AfterStaged extends TaskStageProtocol

    case class StageSuccess[M: ClassTag](ref: ActorRef[TaskProtocol],
                                         matValue: M,
                                         override val replyTo: ActorRef[TaskStageProtocol])
        extends AfterStaged

    case class StageFailure[M: ClassTag](ex: Throwable, override val replyTo: ActorRef[TaskStageProtocol])
        extends AfterStaged
  }

  import TaskStageProtocol._
  import TaskProtocol._

  private implicit val timeout = Task.DefaultTaskSpawnTimeout

  private def guardian(stage: TaskStage): Behavior[TaskStageProtocol] = Behaviors.setup { context =>
    processStageRequest(stage).orElse {
      Behaviors.receiveMessagePartial {
        case aft: AfterStaged =>
          aft.replyTo ! aft
          Behaviors.same

        case _ =>
          Behaviors.same
      }
    }
  }

  private def processStageRequest(stage: TaskStage): Behavior[TaskStageProtocol] = Behaviors.receivePartial {
    case (context, Stage(task, replyTo)) =>
      val actor = context.spawn(task.behavior, task.ref)
      context.ask(actor)(Spawn) {
        case Success(SpawnRetValue(mat)) => StageSuccess(actor, mat, replyTo)
        case Success(_) => throw new IllegalAccessError("It might be race condition occurred here.")
        case Failure(ex) => StageFailure(ex, replyTo)
      }
      Behaviors.same
  }
}
