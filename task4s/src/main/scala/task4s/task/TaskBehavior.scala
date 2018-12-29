package task4s.task

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import task4s.task.TaskBehavior.{Eval, TaskControlProtocol}

/**
 * Behavior (actor) wraps a task instance for allocation.
 * The local one will be called directly, and the cluster one will be wrapped into sharding entity.
 *
 * This cake pattern helps the polymorphic calls between local task and cluster task.
 */
private[task4s] trait TaskBehavior { this: Task =>
  val behavior: Behavior[TaskControlProtocol]

  def spawn(context: ActorContext[_]): ActorRef[TaskControlProtocol] = {
    val ref = context.spawn(behavior, this.ref.value)
    ref ! Eval
    ref
  }
}

private[task4s] object TaskBehavior {

  sealed trait TaskControlProtocol
  case object Eval extends TaskControlProtocol
  case object Shutdown extends TaskControlProtocol

  /**
   * Basic unit behavior of a task.
   *
   * The cluster one will wraps this into sharding version.
   *
   * @param task Task instance wrapped for allocation.
   * @return Behavior of task control.
   */
  def pure(task: Task): Behavior[TaskControlProtocol] =
    Behaviors.receiveMessage {
      case Eval =>
        task.eval()
        Behaviors.same

      case Shutdown =>
        task.shutdown()
        Behaviors.stopped(gracefulShutdownTask(task))
    }

  private def gracefulShutdownTask(task: Task): Behavior[TaskControlProtocol] = Behaviors.receiveSignal {
    case (ctx, PostStop) =>
      ctx.log.info(s"Stop task $task behavior after performing some cleanup hook.")
      Behaviors.same
  }

  def spawn[T <: TaskBehavior](task: T, context: ActorContext[_]): ActorRef[TaskControlProtocol] =
    task.spawn(context)
}
