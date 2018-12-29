package task4s.task.par

/**
 * Define the cluster-wide parallelism strategy.
 *
 * @param replicas Number of replicated tasks.
 * @param roles Set of nodes (predicates) to allocate.
 */
class ParStrategy private (val replicas: Int, val roles: Set[String]) {}

object ParStrategy {

  /**
   * Default parallel allocation strategy, which contains only 1 replica.
   *
   * Note that this is still possible '''parallel''' as possible via underlying akka stream model,
   * i.e. using async operator.
   */
  val DefaultParStrategy = ParStrategy(replicas = 1, roles = Set.empty)

  def apply(replicas: Int, roles: Set[String]): ParStrategy = new ParStrategy(replicas, roles)
}
