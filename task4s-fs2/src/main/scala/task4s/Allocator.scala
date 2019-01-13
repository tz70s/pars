package task4s

import fs2.Stream

/**
 * The polymorphic allocation interface for allocating tasks.
 * Similar to Execution Context, the allocator will be scoped via implicit to flexible **shifting** allocation instance.
 */
trait Allocator {
  def allocate[F[_], T](task: Task[F, T], strategy: Strategy): Stream[F, T]
}

/**
 * The _Self_ node allocator implementation for spawning tasks locally.
 * Simply powered by FS2 scheduling.
 *
 * INTERNAL API.
 */
private[task4s] class SelfAllocatorImpl extends Allocator {
  override def allocate[F[_], T](task: Task[F, T], strategy: Strategy): Stream[F, T] = ???
}

/**
 * Denote the task allocation strategy, binding when allocation is invoked.
 *
 * @param replicas Number of tasks replicated to cluster-wide nodes.
 * @param roles Constraint for task affinity.
 */
case class Strategy(replicas: Int, roles: List[String])

/**
 * Bridge configuration and allocation strategies.
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
