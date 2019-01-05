package task4s.task

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import task4s.task.TaskStage.TaskStageProtocol
import task4s.task.TaskStage.TaskStageProtocol.StageSuccess

import scala.reflect.ClassTag

/**
 * Behavior (actor) wraps a task instance for allocation.
 * The local one will be called directly, and the cluster one will be wrapped into sharding entity.
 *
 * This cake pattern helps the polymorphic calls between local task and cluster task.
 */
private[task4s] trait TaskBehavior { this: Task[_] =>

  import TaskProtocol._

  /**
   * Behavior (actor) of the derived task.
   */
  private[task4s] val behavior: Behavior[TaskProtocol] = pure

  /**
   * Basic unit behavior of a task.
   *
   * The cluster one will wraps this into sharding version.
   *
   * @return Behavior of task control.
   */
  protected[task4s] def pure: Behavior[TaskProtocol] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage {
      case Spawn(replyTo) =>
        val matValue = spawn()
        replyTo ! StageSuccess(ctx.self, matValue, replyTo)
        Behaviors.same

      case Shutdown =>
        Behaviors.stopped(gracefulShutdownTask)

      case _ =>
        Behaviors.same
    }
  }

  private def gracefulShutdownTask: Behavior[TaskProtocol] = Behaviors.receiveSignal {
    case (ctx, PostStop) =>
      ctx.log.info(s"Stop task $this behavior after performing some cleanup hook.")
      Behaviors.same
  }
}

private[task4s] object TaskProtocol {
  sealed trait TaskProtocol

  case class Spawn(replyTo: ActorRef[TaskStageProtocol]) extends TaskProtocol
  case class SpawnRetValue[M: ClassTag](mat: M) extends TaskProtocol
  case object Shutdown extends TaskProtocol
}
