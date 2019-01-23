package pars

import fs2.Stream

/**
 * Channel for pars composition.
 *
 * It's actually only a meta information for internal routing service to dispatch stream to specific pars.
 * Cause each pars binds a specific channel, in any.
 *
 * Note that the id field is important to both channel and pars,
 * b.c. it's also the identifier for cluster-wide pars allocation,
 * i.e. to decide accurate replicas of Machines, we'll use the id to coordinate around all participating nodes.
 *
 * @see ChannelOutputStrategy
 * @param id The unique identifier for channel.
 * @param strategy Channel output strategy.
 */
final case class Channel[+T](id: String, strategy: ChannelOutputStrategy = ChannelOutputStrategy.Concurrent) {

  /**
   * Publish stream of value T into channel, the output strategy is specify by the [[ChannelOutputStrategy]].
   *
   * Note that the implicit spawn binding should be taken in scope for channel resolution.
   *
   * @example {{{
   * val pubStream = Stream(1, 2, 3)
   * val pubResultStream = Channel[Int]("IntStream").pub(pubStream)
   * }}}
   *
   * @param source Source stream to publish.
   * @return Return the publish evaluation result.
   */
  def pub[F[_], U >: T](source: Stream[F, U])(implicit parEffect: ParEffect[F]): Stream[F, Unit] =
    parEffect.send(this, source)

  /**
   * Subscribe stream of value T from channel, the output strategy is specify by the [[ChannelOutputStrategy]]
   *
   * Note that the implicit spawn binding should be taken in scope for channel resolution.
   *
   * @example {{{
   * val subStream = Channel[Int]("IntStream").sub
   * }}}
   *
   * @return The subscribed stream to concat with other stream to deal with.
   */
  def sub[F[_]: ParEffect]: Stream[F, T] = ???
}

object Channel {
  val ChannelSize: Int = pureconfig.loadConfigOrThrow[Int]("pars.channel.size")
}

sealed trait ChannelOutputStrategy extends Serializable

object ChannelOutputStrategy {
  case object Concurrent extends ChannelOutputStrategy
  case object Broadcast extends ChannelOutputStrategy
}
