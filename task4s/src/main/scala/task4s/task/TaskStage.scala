package task4s.task

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.stream.typed.scaladsl.ActorMaterializer

/**
 * The execution instance and associated context.
 *
 * @param name Name for task stage namespace, will propagate to underlying [[akka.actor.typed.ActorSystem]] name.
 */
class TaskStage private (val name: String) {
  import TaskStage._

  /**
   * Access internal actor system for flexibility, i.e. spawn custom actor, using dispatcher, etc.
   */
  val system: ActorSystem[TaskStageProtocol] = ActorSystem(guardian, name)

  /**
   * Stream materializer factored via default actor system.
   */
  val materializer: ActorMaterializer = ActorMaterializer()(system)
}

private[task4s] object TaskStage {

  def apply(name: String): TaskStage = new TaskStage(name)

  /**
   * Task stage related protocols.
   */
  sealed trait TaskStageProtocol
  case object StartUp extends TaskStageProtocol
  case object Shutdown extends TaskStageProtocol

  private val lifecycle: Behavior[TaskStageProtocol] = Behaviors.receivePartial {
    case (ctx, StartUp) =>
      ctx.log.info(s"Spawn Task4s background process.")
      Behaviors.same
    case (_, Shutdown) =>
      Behaviors.stopped
  }

  private val guardian: Behavior[TaskStageProtocol] = lifecycle
}
