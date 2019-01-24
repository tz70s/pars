package pars

import cats.effect.{Concurrent, ContextShift, Timer}
import fs2.concurrent.Queue
import fs2.{RaiseThrowable, Stream}

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
   * val pubResultStream = Channel[Int]("IntStream").send(pubStream)
   * }}}
   *
   * @param source Source stream to publish.
   * @return Return the publish evaluation result but nothing.
   */
  def send[F[_], U >: T](source: Stream[F, U])(implicit pe: ParEffect[F]): Stream[F, Unit] =
    pe.send(this, source)

  /**
   * Send an empty stream to channel, which is useful to trigger ParsM instance which has no receiver stream.
   */
  def unit[F[_]]()(implicit pe: ParEffect[F]): Stream[F, Unit] =
    pe.send(this, Stream.empty.covary[F])

  /**
   * Subscribe stream of value T from channel, the output strategy is specify by the [[ChannelOutputStrategy]]
   *
   * Note that the implicit spawn binding should be taken in scope for channel resolution.
   *
   * @example {{{
   * val receive = Channel[Int]("IntStream").receive
   * }}}
   *
   * @return The subscribed stream to concat with other stream to deal with.
   */
  def receive[F[_]: ParEffect: Concurrent: ContextShift: Timer]: Stream[F, T] = bind(this)

  /**
   * INTERNAL API.
   *
   * Bind an implicit receiver pars for identification and runs out of deque.
   *
   * @param channel Channel for receiving.
   * @return Stream of channel values.
   */
  private def bind[F[_]: Concurrent: ContextShift: Timer: RaiseThrowable, T](
      channel: Channel[T]
  )(implicit ev: ParEffect[F]): Stream[F, T] = {
    val receiver = implicitReceiver(channel)

    for {
      q <- Stream.eval(Queue.boundedNoneTerminated[F, T](1024))
      _ <- ev.spawn(receiver)
      _ <- ev.server.subscribe(channel, q)
      s <- q.dequeue
    } yield s
  }

  private def implicitReceiver[F[_], I](channel: Channel[I])(implicit ev: ParEffect[F]): Pars[F, I, I] =
    Pars.concat(channel, Channel.NotUsed, Strategy(replicas = 1, model = Stick()))(s => s)
}

object Channel {
  val ChannelSize: Int = pureconfig.loadConfigOrThrow[Int]("pars.channel.size")

  val NotUsed = Channel("not-used", ChannelOutputStrategy.NotUsed)
}

sealed trait ChannelOutputStrategy extends Serializable

object ChannelOutputStrategy {
  case object Concurrent extends ChannelOutputStrategy
  case object Broadcast extends ChannelOutputStrategy
  case object NotUsed extends ChannelOutputStrategy
}
