package pars.cluster

import fs2.Stream
import pars.internal.{Protocol, UnsafeChannel, UnsafePars}
import pars.internal.remote.tcp.TcpSocketConfig

trait Coordinator[F[_]] {

  val address: TcpSocketConfig

  /**
   * Serving coordinator.
   *
   * @return Stream of launched.
   */
  def bindAndHandle(): Stream[F, Unit]
}

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
  def get[F[_]](clazz: Class[Coordinator[F]], args: Any*): Coordinator[F] = clazz.newInstance()

  /**
   * Load a coordinator from a given class name.
   *
   * @example {{{
   * val coordinator = CoordinatorProvider.getFromName("pars.cluster.internal.StandAloneCoordinator")
   * }}}
   *
   * @param clazzName Class name for coordinator.
   * @return Coordinator instance.
   */
  def getFromName[F[_]](clazzName: String): Coordinator[F] =
    Class.forName(clazzName).newInstance().asInstanceOf[Coordinator[F]]
}

object CoordinationProtocol {

  import Protocol._

  /**
   * Control plane protocol.
   *
   * 1. Stage requests to coordinator for pars allocation.
   * 2. Coordinator command worker node to spawn pars.
   */
  sealed trait CoordinationProtocol extends Protocol

  // Marker traits for dual relation.

  sealed trait ProxyToCoordinator extends CoordinationProtocol
  sealed trait CoordinatorToProxy extends CoordinationProtocol

  // Health check

  case class Ping(address: TcpSocketConfig) extends ProxyToCoordinator
  case object Pong extends CoordinatorToProxy

  // Allocation related protocol.

  /**
   * Request allocation to coordinator.
   *
   * @param pars Allocated pars.
   */
  case class AllocationRequest[F[_]](pars: UnsafePars[F]) extends ProxyToCoordinator

  /**
   * Indicate request allocation success.
   *
   * @param pars Pars reference for tracking.
   */
  case class RequestOk[F[_]](pars: UnsafePars[F], workers: Seq[TcpSocketConfig]) extends CoordinatorToProxy

  /**
   * Indicate request allocation failure.
   *
   * @param throwable Error cause.
   */
  case class RequestErr(throwable: Throwable) extends CoordinatorToProxy

  case class EntryLookUpRequest(channel: UnsafeChannel) extends ProxyToCoordinator

  sealed trait Command extends CoordinatorToProxy

  /**
   * Command worker node (self) to spawn pars instance.
   *
   * @param pars Allocated pars.
   */
  case class AllocationCommand[F[_]](pars: UnsafePars[F], workers: Seq[TcpSocketConfig]) extends Command

  /**
   * Command worker node (self) to remove pars instance.
   *
   * @param channel Channel ref for tracking.
   */
  case class RemovalCommand(channel: UnsafeChannel) extends Command

  /**
   * Indicate command allocation success.
   *
   * @param channel Channel reference for tracking.
   */
  case class CommandOk(channel: UnsafeChannel) extends ProxyToCoordinator

  /**
   * Indicate command allocation failure.
   *
   * @param throwable Error cause.
   */
  case class CommandErr(throwable: Throwable) extends ProxyToCoordinator

  case class NoAvailableWorker(message: String) extends Exception(message)

  case class EntryLookUpException(message: String) extends Exception(message)
}
