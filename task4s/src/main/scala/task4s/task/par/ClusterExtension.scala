package task4s.task.par

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

class ClusterExtension(implicit system: ActorSystem[_]) {
  val shard: ClusterSharding = ClusterSharding(system)
}
