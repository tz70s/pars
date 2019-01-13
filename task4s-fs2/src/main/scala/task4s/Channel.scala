package task4s

final case class Channel[F[_], +T](ref: ChannelRef, strategy: ChannelOutputStrategy = Concurrent)

object Channel {

  val ChannelSize: Int = pureconfig.loadConfigOrThrow[Int]("task4s.channel.size")

  def apply[F[_], T](ref: ChannelRef, strategy: ChannelOutputStrategy = Concurrent): Channel[F, T] =
    new Channel(ref, strategy)
}

final case class ChannelRef(value: String)

sealed trait ChannelOutputStrategy
case object Concurrent extends ChannelOutputStrategy
case object Broadcast extends ChannelOutputStrategy

class ChannelService {}
