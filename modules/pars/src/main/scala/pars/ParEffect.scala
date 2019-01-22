package pars

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Timer}
import pars.internal.ParServer
import pars.internal.remote.tcp.TcpSocketConfig

/**
 * The ParEffect '''distributed''' a pars into stream.
 *
 * The polymorphic allocation interface for allocating tasks.
 * Similar to Execution Context, the assembler will be scoped via implicit to flexible **shifting** allocate pars.
 */
trait ParEffect[F[_]] {

  val server: ParServer[F]
}

class ParEffectOp[F[_]: Concurrent: ContextShift: Timer] private[pars] (implicit acg: AsynchronousChannelGroup) {

  /**
   * Create a ParEffect instance with binding addresses of coordinators.
   *
   * @example {{{
   * implicit val pe = ParEffect[IO].bindCoordinators(Seq(someAddresses))
   * }}}
   *
   * @param coordinators Address of coordinators.
   * @return ParEffect instance.
   */
  def bindCoordinators(coordinators: Seq[TcpSocketConfig]): ParEffect[F] =
    new ParEffect[F] {
      override val server: ParServer[F] = new ParServer[F](coordinators)
    }

  /**
   * Omit coordinator, use for phantom par effect place holder.
   *
   * @return ParEffect instance.
   */
  def localAndOmitCoordinator: ParEffect[F] =
    new ParEffect[F] {
      override val server: ParServer[F] = null
    }

}

object ParEffect {

  /**
   * Convenience application to find the implicit allocate pars.
   *
   * @example {{{
   * // Resolution for allocate pars.
   * val allocate = ParEffect[F]
   *
   * // Operations.
   * val eval = ParEffect[F].loadAndOmitCoordinator
   * }}}
   *
   * @return Implicit ParEffect instance.
   */
  def apply[F[_]: Concurrent: ContextShift: Timer](implicit acg: AsynchronousChannelGroup): ParEffectOp[F] =
    new ParEffectOp[F]()
}

/**
 * Denote the pars distribution strategy, binding when evaluation is invoked.
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
private[pars] object StrategyBackend {
  val roles: Set[String] = pureconfig.loadConfig[List[String]]("pars.strategy.roles").getOrElse(List.empty).toSet

  sealed trait RoleMatchOrNot
  case object Yes extends RoleMatchOrNot
  case object No extends RoleMatchOrNot

  def matchOrNot(strategy: Strategy): RoleMatchOrNot = {
    val intersect = strategy.roles.toSet & roles
    if (intersect.isEmpty) No else Yes
  }
}
