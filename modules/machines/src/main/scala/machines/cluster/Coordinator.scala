package machines.cluster
import machines.{Channel, FlyingMachine, Strategy}
import machines.internal.Protocol

abstract class Coordinator

object CoordinatorProvider {

  /**
   * Load a coordinator from given class.
   *
   * @example {{{
   * val coordinator = CoordinatorProvider.get(classOf[StandAloneCoordinator])
   * }}}
   *
   * TODO reflect the constructor for new instance.
   *
   * @param clazz Providing class of coordinator.
   * @return Coordinator instance.
   */
  def get(clazz: Class[Coordinator], args: Any*): Coordinator = clazz.newInstance()

  /**
   * Load a coordinator from a given class name.
   *
   * @example {{{
   * val coordinator = CoordinatorProvider.getFromName("machines.cluster.internal.StandAloneCoordinator")
   * }}}
   *
   * @param clazzName Class name for coordinator.
   * @return Coordinator instance.
   */
  def getFromName(clazzName: String): Coordinator = Class.forName(clazzName).newInstance().asInstanceOf[Coordinator]
}

object CoordinationProtocol {

  import Protocol._

  /**
   * Control plane protocol.
   *
   * 1. Stage requests to coordinator for machine allocation.
   * 2. Coordinator command worker node to allocate machine.
   */
  sealed trait CoordinationProtocol extends Protocol

  // Marker traits for dual relation.

  sealed trait ProxyToCoordinator extends CoordinationProtocol
  sealed trait CoordinatorToProxy extends CoordinationProtocol

  // Allocation related protocol.

  /**
   * Request allocation to coordinator.
   *
   * @param machine Allocated [[machines.Machine]].
   * @param strategy Allocation strategy.
   */
  case class AllocationRequest[F[_], -I, +O](machine: FlyingMachine[F, I, O], strategy: Strategy)
      extends ProxyToCoordinator

  /**
   * Indicate request allocation success.
   *
   * @param channel Channel reference for tracking.
   */
  case class RequestOk[F[_]](channel: Channel[_]) extends CoordinatorToProxy

  /**
   * Indicate request allocation failure.
   *
   * @param throwable Error cause.
   */
  case class RequestErr(throwable: Throwable) extends CoordinatorToProxy

  sealed trait Command extends CoordinatorToProxy

  /**
   * Command worker node (self) to allocate machine instance.
   *
   * @param machine Allocated [[machines.Machine]]
   */
  case class AllocationCommand[F[_]](machine: FlyingMachine[F, _, _]) extends Command

  /**
   * Command worker node (self) to remove machine instance.
   *
   * @param channel Channel ref for tracking.
   */
  case class RemovalCommand(channel: Channel[_]) extends Command

  /**
   * Indicate command allocation success.
   *
   * @param channel Channel reference for tracking.
   */
  case class CommandOk(channel: Channel[_]) extends ProxyToCoordinator

  /**
   * Indicate command allocation failure.
   *
   * @param throwable Error cause.
   */
  case class CommandErr(throwable: Throwable) extends ProxyToCoordinator
}
