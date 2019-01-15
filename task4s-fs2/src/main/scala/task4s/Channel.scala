package task4s

/**
 * Channel for machine composition.
 *
 * It's actually only a meta information for internal routing service to dispatch stream to specific machine.
 * Cause each machine binds a specific channel, in any.
 *
 * Note that the id field is important to both channel and machine,
 * b.c. it's also the identifier for cluster-wide machine allocation,
 * i.e. to decide accurate replicas of Machines, we'll use the id to coordinate around all participating nodes.
 *
 * @see ChannelOutputStrategy
 *
 * @param id The unique identifier for channel.
 * @param strategy Channel output strategy.
 */
final case class Channel[+T](id: String, strategy: ChannelOutputStrategy)

object Channel {
  val ChannelSize: Int = pureconfig.loadConfigOrThrow[Int]("task4s.channel.size")

  def apply[T](ref: String, strategy: ChannelOutputStrategy = ChannelOutputStrategy.Concurrent): Channel[T] =
    new Channel(ref, strategy)
}

sealed trait ChannelOutputStrategy extends Serializable

object ChannelOutputStrategy {
  case object Concurrent extends ChannelOutputStrategy
  case object Broadcast extends ChannelOutputStrategy
}
