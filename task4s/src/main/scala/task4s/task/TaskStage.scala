package task4s.task

import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.util.Timeout
import task4s.task.TaskProtocol.TaskProtocol
import task4s.task.par.ClusterExtension

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * The execution instance and associated context.
 *
 * @param name Name for task stage namespace, will propagate to underlying [[akka.actor.typed.ActorSystem]] name.
 */
class TaskStage private (val name: String) {
  import TaskStage._

  private[task4s] val tasks = TrieMap[String, Task[_]]()

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
   * Lookup actor reference via task.
   *
   * @param task Lookup task.
   * @return Optional value of lookup actor reference.
   */
  private[task4s] def apply[M](task: Task[M]): Option[ActorRef[TaskProtocol]] = activeTasks.get(task.ref)

  /**
   * Lookup actor reference via task reference.
   *
   * @param ref Reference of lookup task.
   * @return Optional value of lookup actor reference.
   */
  private[task4s] def apply(ref: String): Option[ActorRef[TaskProtocol]] = activeTasks.get(ref)

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
  private[task4s] sealed trait TaskStageProtocol

  sealed trait TaskStageProtocolWithRef extends TaskStageProtocol {
    val replyTo: ActorRef[TaskStageProtocol]
  }

  private[task4s] object TaskStageProtocol {

    case class LocalStage[M: ClassTag](task: Task[M], override val replyTo: ActorRef[TaskStageProtocol])
        extends TaskStageProtocolWithRef

    case class ClusterStage[M: ClassTag](task: Task[M], override val replyTo: ActorRef[TaskStageProtocol])
        extends TaskStageProtocolWithRef

    // Still carry class tag for remote type checking.
    case class RemoteStage[M: ClassTag](ref: String, override val replyTo: ActorRef[TaskStageProtocol])
        extends TaskStageProtocolWithRef

    sealed trait AfterStaged extends TaskStageProtocolWithRef

    case class StageSuccess[M: ClassTag](ref: ActorRef[TaskProtocol],
                                         matValue: M,
                                         override val replyTo: ActorRef[TaskStageProtocol])
        extends AfterStaged

    case class StageFailure[M: ClassTag](ex: Throwable, override val replyTo: ActorRef[TaskStageProtocol])
        extends AfterStaged

    case class UpdateClusterStageProviders(set: Set[ActorRef[TaskStageProtocol]]) extends TaskStageProtocol
  }

  import TaskStageProtocol._
  import TaskProtocol._

  private implicit val timeout: Timeout = Task.DefaultTaskSpawnTimeout

  val StageServiceKey: ServiceKey[TaskStageProtocol] = ServiceKey[TaskStageProtocol]("StageServiceKey")

  private def guardian(stage: TaskStage): Behavior[TaskStageProtocol] =
    Behaviors.setup { context =>
      val service = context.spawn(stageService(stage), "StageService")
      Behaviors.receiveMessage { msg =>
        service ! msg
        Behaviors.same
      }
    }

  private def stageService(stage: TaskStage): Behavior[TaskStageProtocol] = Behaviors.setup { context =>
    context.system.receptionist ! Receptionist.Register(StageServiceKey, context.self)

    context.spawn(stageServiceMonitor(Set.empty, context.self), "StageServiceMonitor")

    val StashBufferSize = 100

    val buffer = StashBuffer[TaskStageProtocol](StashBufferSize)

    def init(): Behavior[TaskStageProtocol] = Behaviors.receiveMessage {
      case UpdateClusterStageProviders(set) if set.nonEmpty =>
        context.log.info(s"Bumping task request queue of size ${buffer.size}")
        buffer.unstashAll(context, active(set))
      case msg =>
        if (!buffer.isFull) buffer.stash(msg) else context.log.warning(s"Stash buffer overflow, dropping message $msg")
        Behaviors.same
    }

    def active(set: Set[ActorRef[TaskStageProtocol]]): Behavior[TaskStageProtocol] =
      taskStageProvider(stage, set).orElse {
        Behaviors.receiveMessagePartial {
          case aft: AfterStaged =>
            aft.replyTo ! aft
            Behaviors.same
          case UpdateClusterStageProviders(_set) =>
            active(_set)
          case _ =>
            Behaviors.same
        }
      }

    init()
  }

  private def taskStageProvider(stage: TaskStage, set: Set[ActorRef[TaskStageProtocol]]): Behavior[TaskStageProtocol] =
    Behaviors.receivePartial {
      case (context, LocalStage(task, replyTo)) =>
        // Checkout if existed.
        val actor = stage(task) match {
          case Some(ref) => ref
          case None =>
            val _actor = context.spawn(task.behavior, task.ref)
            stage += task -> _actor
            _actor
        }
        actor ! Spawn(replyTo)
        Behaviors.same

      case (_, ClusterStage(task, replyTo)) =>
        // Temporarily workaround, simply convert cluster stage message into local stage one, and send to the cluster registered actors.
        // Randomly pick one of registered actors.
        select(set) match {
          case Some(ref) => ref ! RemoteStage(task.ref, replyTo)
          case None => replyTo ! StageFailure(TaskStageError("No available cluster resource."), replyTo)
        }
        Behaviors.same

      case (context, RemoteStage(ref, replyTo)) =>
        stage.tasks.get(ref) match {
          case Some(task) => context.self ! LocalStage(task, replyTo)
          case None => replyTo ! StageFailure(TaskNotFoundError(s"Not found task for $ref"), replyTo)
        }
        Behaviors.same
    }

  private def select(set: Set[ActorRef[TaskStageProtocol]]): Option[ActorRef[TaskStageProtocol]] =
    if (set.nonEmpty) {
      val idx = scala.util.Random.nextInt(set.size)
      val indexedSeq = set.toIndexedSeq
      if (idx < set.size && idx >= 0) {
        Some(indexedSeq(idx))
      } else None
    } else None

  private def stageServiceMonitor(stages: Set[ActorRef[TaskStageProtocol]],
                                  parent: ActorRef[TaskStageProtocol]): Behavior[Listing] = Behaviors.setup { ctx =>
    ctx.system.receptionist ! Receptionist.Subscribe(StageServiceKey, ctx.self)

    def internal(lst: Set[ActorRef[TaskStageProtocol]]): Behavior[Listing] =
      Behaviors.receiveMessage {
        case StageServiceKey.Listing(listing) if listing.nonEmpty =>
          ctx.log.info(s"Stage service listing update, now available: $listing")
          parent ! UpdateClusterStageProviders(listing)
          internal(listing)

        case _ =>
          Behaviors.same
      }
    internal(Set.empty)
  }
}
