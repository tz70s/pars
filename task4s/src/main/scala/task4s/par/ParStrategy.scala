package task4s.par

/**
 * Define the cluster-wide parallelism strategy.
 *
 * @param replicas Number of replicated tasks.
 * @param roles Set of nodes (predicates) to allocate.
 */
class ParStrategy private (val replicas: Int, val roles: Set[String]) {}

object ParStrategy {

  /**
   * Default anti-cluster allocation strategy.
   *
   * Note that this is still possible '''parallel''' as possible via underlying akka stream model, but executed in local.
   */
  val GroundStrategy = ParStrategy(replicas = 1, roles = Set.empty)

  def apply(replicas: Int, roles: Set[String]): ParStrategy = new ParStrategy(replicas, roles)
}
