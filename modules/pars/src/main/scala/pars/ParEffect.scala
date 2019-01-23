package pars

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Timer}
import pars.internal.ParServer
import pars.internal.remote.tcp.TcpSocketConfig
import fs2.{RaiseThrowable, Stream}

import scala.collection.concurrent.TrieMap

/**
 * The ParEffect '''distributed''' a pars into stream.
 *
 * The polymorphic allocation interface for allocating tasks.
 * Similar to Execution Context, the assembler will be scoped via implicit to flexible **shifting** spawn pars.
 */
trait ParEffect[F[_]] extends Serializable {

  @transient private[pars] val server: ParServer[F]

  val coordinators: Seq[TcpSocketConfig]

  def spawn[I, O](pars: Pars[F, I, O]): Stream[F, Channel[I]] = server.spawn(pars)

  def send[I](channel: Channel[I], events: Stream[F, I]): Stream[F, Unit] = server.send(channel, events)
}

private[pars] object ParEffectOp {

  private trait Phantom[T]

  private val pool = TrieMap[Seq[TcpSocketConfig], ParEffect[Phantom]]()

  def findExisted[F[_]](addresses: Seq[TcpSocketConfig]): Stream[F, ParEffect[F]] =
    Stream.emit(pool(addresses).asInstanceOf[ParEffect[F]])

  def record[F[_]](pe: ParEffect[F]): Unit = pool += (pe.coordinators -> pe.asInstanceOf[ParEffect[Phantom]])
}

class ParEffectOp[F[_]: Concurrent: ContextShift: Timer: RaiseThrowable] private[pars] (
    implicit acg: AsynchronousChannelGroup
) {

  /**
   * Create a ParEffect instance with binding addresses of coordinators.
   *
   * @example {{{
   * implicit val pe = ParEffect[IO].bindCoordinators(Seq(someAddresses))
   * }}}
   *
   * @param addresses Address of coordinators.
   * @return ParEffect instance.
   */
  def bindCoordinators(addresses: Seq[TcpSocketConfig]): ParEffect[F] = {
    val pe = new ParEffect[F] {
      override val coordinators: Seq[TcpSocketConfig] = addresses

      @transient override val server: ParServer[F] = new ParServer[F](coordinators)
    }
    ParEffectOp.record(pe)
    pe
  }

  /**
   * Create a ParEffect instance with binding address of coordinator.
   *
   * @example {{{
   * implicit val pe = ParEffect[IO].bindCoordinator(someAddress)
   * }}}
   *
   * @param address Address of coordinator.
   * @return ParEffect instance.
   */
  def bindCoordinator(address: TcpSocketConfig): ParEffect[F] = bindCoordinators(Seq(address))

  /**
   * Omit coordinator, use for phantom par effect place holder.
   *
   * @return ParEffect instance.
   */
  def localAndOmitCoordinator: ParEffect[F] =
    new ParEffect[F] {
      override val coordinators: Seq[TcpSocketConfig] = Seq.empty
      @transient override val server: ParServer[F] = null
    }

}

object ParEffect {

  /**
   * Convenience application to find the implicit spawn pars.
   *
   * @example {{{
   * // Resolution for spawn pars.
   * val spawn = ParEffect[F]
   *
   * // Operations.
   * val eval = ParEffect[F].loadAndOmitCoordinator
   * }}}
   *
   * @return Implicit ParEffect instance.
   */
  def apply[F[_]: Concurrent: ContextShift: Timer: RaiseThrowable](
      implicit acg: AsynchronousChannelGroup
  ): ParEffectOp[F] =
    new ParEffectOp[F]()

}

/**
 * Denote the pars distribution strategy, binding when evaluation is invoked.
 *
 * @param replicas Number of tasks replicated to cluster-wide nodes.
 * @param roles Constraint for task affinity.
 */
case class Strategy(replicas: Int, roles: List[String] = List.empty, model: EvaluationModel = Evaluate)

sealed trait EvaluationModel
case object NoEvaluate extends EvaluationModel
case object Evaluate extends EvaluationModel

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
