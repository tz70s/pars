package task4s.task

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import task4s.task.TaskBehavior.{Eval, TaskBehaviorProtocol}

/**
 * Behavior (actor) wraps a task instance for allocation.
 * The local one will be called directly, and the cluster one will be wrapped into sharding entity.
 *
 * This cake pattern helps the polymorphic calls between local task and cluster task.
 */
private[task4s] trait TaskBehavior { this: Task =>

  /**
   * Behavior (actor) of the derived task.
   */
  private[task4s] val behavior: Behavior[TaskBehaviorProtocol]

  /**
   * Spawn the task.
   *
   * @param context Actor context passed via closure like Behaviors.setup.
   * @return Actor reference of task behavior.
   */
  private[task4s] def spawn(context: ActorContext[_]): ActorRef[TaskBehaviorProtocol] = {
    val ref = context.spawn(behavior, this.ref.value)
    ref ! Eval
    ref
  }
}

private[task4s] object TaskBehavior {

  sealed trait TaskBehaviorProtocol
  case object Eval extends TaskBehaviorProtocol
  case object Shutdown extends TaskBehaviorProtocol

  /**
   * Basic unit behavior of a task.
   *
   * The cluster one will wraps this into sharding version.
   *
   * @param task Task instance wrapped for allocation.
   * @return Behavior of task control.
   */
  def pure(task: Task): Behavior[TaskBehaviorProtocol] =
    Behaviors.receiveMessage {
      case Eval =>
        task.eval()
        Behaviors.same

      case Shutdown =>
        Behaviors.stopped(gracefulShutdownTask(task))
    }

  private def gracefulShutdownTask(task: Task): Behavior[TaskBehaviorProtocol] = Behaviors.receiveSignal {
    case (ctx, PostStop) =>
      ctx.log.info(s"Stop task $task behavior after performing some cleanup hook.")
      Behaviors.same
  }

  def spawn[T <: TaskBehavior](task: T, context: ActorContext[_]): ActorRef[TaskBehaviorProtocol] =
    task.spawn(context)
}
