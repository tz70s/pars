package task4s

import cats.data.OptionT
import cats.effect.{Concurrent, Sync}

import scala.collection.concurrent.TrieMap

final case class Channel[F[_]: Concurrent, T](ref: ChannelRef,
                                              strategy: ChannelOutputStrategy = ChannelOutputStrategy.Concurrent)

object Channel {

  val ChannelSize: Int = pureconfig.loadConfigOrThrow[Int]("task4s.channel.size")

  def apply[F[_]: Concurrent, T](ref: ChannelRef,
                                 strategy: ChannelOutputStrategy = ChannelOutputStrategy.Concurrent): Channel[F, T] =
    new Channel(ref, strategy)
}

final case class ChannelRef(value: String)

sealed trait ChannelOutputStrategy

object ChannelOutputStrategy {
  case object Concurrent extends ChannelOutputStrategy
  case object Broadcast extends ChannelOutputStrategy
}

/**
 * ChannelService records local cache of channel instances.
 *
 * Note: the current implementation based on TrieMap is side-effected.
 *
 * @param sync$F Require Sync context bound for evaluate into effect context.
 * @tparam F Effect type.
 */
private[task4s] class ChannelService[F[_]: Sync]() {

  private val channels = TrieMap[ChannelRef, Channel[F, _]]()

  def +=[T](channel: Channel[F, T]): F[Unit] =
    Sync[F].delay(channels += (channel.ref -> channel))

  def -=[T](channel: Channel[F, T]): F[Unit] =
    Sync[F].delay(channels -= channel.ref)

  def lookUp(ref: ChannelRef): OptionT[F, Channel[F, _]] =
    OptionT(Sync[F].delay(channels.get(ref)))
}
