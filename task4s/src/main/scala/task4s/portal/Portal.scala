package task4s.portal

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{Sink, Source}

/**
 * Referential channel for service discovery and dynamic task composition.
 *
 * @param name Name of Portal.
 * @tparam T The streaming data type for either akka stream source or sink.
 * @tparam Mat Materialized value for either akka stream source or sink.
 */
class Portal[T, Mat] private (name: String) {

  /**
   * Produce a stream source from this portal.
   *
   * @return Source container in akka stream, with same data type and materialized value specified in portal class.
   */
  def asIn: Source[T, Mat] = ???

  /**
   * Produce a stream sink from this portal.
   *
   * @return Sink container in akka stream, with same data type and materialized value specified in portal class.
   */
  def asOut: Sink[T, Mat] = ???

  override def toString: String = s"Portal($name)"
}

object Portal {
  def apply[T, Mat](name: String): Portal[T, Mat] = new Portal[T, Mat](name)

  sealed trait PortalControlProtocol
  case object AsSource extends PortalControlProtocol

  def controlBehavior(name: String): Behavior[PortalControlProtocol] = Behaviors.receive {
    case (ctx, AsSource) =>
      ctx.log.info(s"Portal($name) create a source ref for streaming.")
      Behaviors.same
    case _ =>
      Behaviors.stopped
  }
}
