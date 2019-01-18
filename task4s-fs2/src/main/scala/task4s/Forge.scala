package task4s

import fs2.Stream

/**
 * The Forge '''forge''' a machine into stream.
 *
 * The polymorphic allocation interface for allocating tasks.
 * Similar to Execution Context, the assembler will be scoped via implicit to flexible **shifting** forge instance.
 */
trait Forge[F[_]] {
  def forge[I, O](machine: Machine[F, I, O], strategy: Strategy): Stream[F, O]
}

object Forge {
  def forge[F[_], I, O](machine: Machine[F, I, O], strategy: Strategy): Stream[F, O] =
    machine.ev.forge(machine, strategy)
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
private[task4s] object StrategyBackend {
  val roles: Set[String] = pureconfig.loadConfig[List[String]]("task4s.strategy.roles").getOrElse(List.empty).toSet

  sealed trait RoleMatchOrNot
  case object Yes extends RoleMatchOrNot
  case object No extends RoleMatchOrNot

  def matchOrNot(strategy: Strategy): RoleMatchOrNot = {
    val intersect = strategy.roles.toSet & roles
    if (intersect.isEmpty) No else Yes
  }
}
