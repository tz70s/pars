package task4s.task

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}

private[task4s] object TaskControlBehavior {

  sealed trait TaskControlProtocol
  case object Eval extends TaskControlProtocol
  case object Stop extends TaskControlProtocol

  /**
   * Behavior (actor) wraps a task instance for allocation.
   * The local one will be called directly, and the cluster one will be wrapped into sharding entity.
   *
   * @param task Task instance wrapped for allocation.
   * @return Behavior of task control.
   */
  def taskBehavior(task: Task): Behavior[TaskControlProtocol] =
    Behaviors.receiveMessage {
      case Eval =>
        task.eval()
        Behaviors.same

      case Stop =>
        task.stop()
        Behaviors.stopped(gracefulShutdownTask(task))
    }

  private def gracefulShutdownTask(task: Task): Behavior[TaskControlProtocol] = Behaviors.receiveSignal {
    case (ctx, PostStop) =>
      ctx.log.info(s"Stop task $task behavior after performing some cleanup hook.")
      Behaviors.same
  }
}

private[task4s] object ClusterLevelBehavior {

  import TaskControlBehavior._

  val TaskControlTypeKey: EntityTypeKey[TaskControlProtocol] = EntityTypeKey[TaskControlProtocol]("TaskControl")

  def createTaskShard(task: Task, system: ActorSystem[_]): ActorRef[ShardingEnvelope[TaskControlProtocol]] = {
    val sharding = ClusterSharding(system)
    sharding.init(
      Entity(
        typeKey = TaskControlTypeKey,
        createBehavior = ctx => taskBehavior(task)
      )
    )
  }
}

private[task4s] object LocalBehavior {}
