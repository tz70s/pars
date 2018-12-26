package task4s.task

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

/**
 * The execution instance and associated context.
 */
class TaskEnv private (name: String) {
  import TaskEnv._

  /**
   * Access internal actor system for flexibility, i.e. spawn custom actor, using dispatcher, etc.
   */
  val system: ActorSystem[TaskEnvProtocol] = ActorSystem(guardian, name)
}

object TaskEnv {

  def apply(name: String): TaskEnv = new TaskEnv(name)

  /**
   * TaskEnv related protocols.
   */
  sealed trait TaskEnvProtocol
  case object StartUp extends TaskEnvProtocol
  case object Shutdown extends TaskEnvProtocol

  private val lifecycle: Behavior[TaskEnvProtocol] = Behaviors.receivePartial {
    case (ctx, StartUp) =>
      ctx.log.info(s"Spawn Task4s background process.")
      Behaviors.same
    case (_, Shutdown) =>
      Behaviors.stopped
  }

  private val guardian: Behavior[TaskEnvProtocol] = lifecycle
}
