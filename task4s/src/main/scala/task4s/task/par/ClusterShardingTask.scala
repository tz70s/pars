package task4s.task.par

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{Entity, EntityTypeKey}
import task4s.task.{Task, TaskBehavior}

object ClusterShardingTask {

  import TaskBehavior._

  val ClusterShardingTaskStrBase = this.getClass.getName.replace("$", "").split("\\.").last

  val ClusterShardingTaskTypeKey: EntityTypeKey[TaskBehaviorProtocol] =
    EntityTypeKey[TaskBehaviorProtocol](ClusterShardingTaskStrBase)

  def create(task: Task)(implicit clusterExt: ClusterExtension): ActorRef[ShardingEnvelope[TaskBehaviorProtocol]] = {
    val shard = clusterExt.shard
    shard.init(
      Entity(
        typeKey = ClusterShardingTaskTypeKey,
        createBehavior = _ => pure(task)
      )
    )
  }
}
