package machines

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Timer}
import fs2.Stream
import machines.internal.ParServer
import machines.internal.remote.tcp.TcpSocketConfig

/**
 * The ParEffect '''allocate''' a machine into stream.
 *
 * The polymorphic allocation interface for allocating tasks.
 * Similar to Execution Context, the assembler will be scoped via implicit to flexible **shifting** allocate instance.
 */
trait ParEffect[F[_]] {

  val server: ParServer[F]
}

object ParEffect {

  def bindCoordinators[F[_]: Concurrent: ContextShift: Timer](
      coordinators: Seq[TcpSocketConfig]
  )(implicit acg: AsynchronousChannelGroup): ParEffect[F] =
    new ParEffect[F] {
      override val server: ParServer[F] = new ParServer[F](coordinators)
    }

  def localAndOmitCoordinator[F[_]: Concurrent: ContextShift: Timer](
      implicit acg: AsynchronousChannelGroup
  ): ParEffect[F] =
    new ParEffect[F] {
      override val server: ParServer[F] = null
    }

  /**
   * Convenience application to find the implicit allocate instance.
   *
   * @example {{{
   * // Resolution for allocate instance.
   * val allocate = ParEffect[F]
   *
   * // Operations.
   * val eval = ParEffect[F].allocate(machine, strategy)
   * }}}
   *
   * @return Implicit ParEffect instance.
   */
  def apply[F[_]: ParEffect]: ParEffect[F] = implicitly[ParEffect[F]]
}

/**
 * Denote the machine assembly strategy, binding when assembly is invoked.
 *
 * @param replicas Number of tasks replicated to cluster-wide nodes.
 * @param roles Constraint for task affinity.
 */
case class Strategy(replicas: Int, roles: List[String] = List.empty)

/**
 * Bridge configuration and assembly strategies.
 *
 * INTERNAL API.
 */
private[machines] object StrategyBackend {
  val roles: Set[String] = pureconfig.loadConfig[List[String]]("machines.strategy.roles").getOrElse(List.empty).toSet

  sealed trait RoleMatchOrNot
  case object Yes extends RoleMatchOrNot
  case object No extends RoleMatchOrNot

  def matchOrNot(strategy: Strategy): RoleMatchOrNot = {
    val intersect = strategy.roles.toSet & roles
    if (intersect.isEmpty) No else Yes
  }
}
