package task4s.task.par

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.typed.{Cluster, Subscribe}

private[task4s] object ClusterStatus {

  /**
   * Monitor cluster member event for maintaining states.
   */
  val monitor: Behavior[ClusterDomainEvent] = Behaviors.setup { ctx =>
    val cluster = Cluster(ctx.system)
    cluster.subscriptions ! Subscribe(ctx.self, classOf[ClusterDomainEvent])

    internalStatusReceiver(Set.empty)
  }

  private def internalStatusReceiver(members: Set[Member]): Behavior[ClusterDomainEvent] =
    reachable(members)
      .orElse(unreachable(members))
      .orElse(Behaviors.receiveMessage(_ => Behaviors.same))

  private def reachable(members: Set[Member]): Behavior[ClusterDomainEvent] = Behaviors.receivePartial {
    case (ctx, MemberUp(mbr)) =>
      ctx.log.info(s"Member $mbr reachable in the cluster.")
      internalStatusReceiver(members + mbr)
  }

  private def unreachable(members: Set[Member]): Behavior[ClusterDomainEvent] = Behaviors.receivePartial {
    case (ctx, UnreachableMember(mbr)) =>
      ctx.log.info(s"Member $mbr unreachable in the cluster.")
      internalStatusReceiver(members - mbr)

    case (ctx, MemberRemoved(mbr, preStatus)) =>
      ctx.log.info(s"Member $mbr get removed in the cluster, previous status: $preStatus")
      internalStatusReceiver(members - mbr)
  }

}
