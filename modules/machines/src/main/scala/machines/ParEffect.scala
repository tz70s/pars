package machines

import fs2.Stream

/**
 * The ParEffect '''spawn''' a machine into stream.
 *
 * The polymorphic allocation interface for allocating tasks.
 * Similar to Execution Context, the assembler will be scoped via implicit to flexible **shifting** spawn instance.
 */
trait ParEffect[F[_]] {

  /**
   * Spawn a machine with specified strategy, and materialized into Stream.
   *
   * @param machine Machine class to spawn.
   * @param strategy Strategy for distribution.
   * @return Materialized stream.
   */
  def spawn[I, O](machine: Machine[F, I, O], strategy: Strategy): Stream[F, O]
}

object ParEffect {

  /**
   * Convenience application to find the implicit spawn instance.
   *
   * @example {{{
   * // Resolution for spawn instance.
   * val spawn = ParEffect[F]
   *
   * // Operations.
   * val eval = ParEffect[F].spawn(machine, strategy)
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
