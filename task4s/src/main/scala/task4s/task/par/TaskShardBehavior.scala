package task4s.task.par

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{Entity, EntityTypeKey}
import task4s.task.{Task, TaskBehavior}

private[task4s] object TaskShardBehavior {

  import TaskBehavior._

  val TaskBehaviorTypeKey: EntityTypeKey[TaskBehaviorProtocol] =
    EntityTypeKey[TaskBehaviorProtocol]("TaskBehavior")

  def create(task: Task)(implicit clusterExt: ClusterExtension): ActorRef[ShardingEnvelope[TaskBehaviorProtocol]] = {
    val shard = clusterExt.shard
    shard.init(
      Entity(
        typeKey = TaskBehaviorTypeKey,
        createBehavior = _ => pure(task)
      )
    )
  }
}
