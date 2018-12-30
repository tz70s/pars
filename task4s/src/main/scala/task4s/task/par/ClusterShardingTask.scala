package task4s.task.par

import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import task4s.task.TaskProtocol.TaskProtocol

object ClusterShardingTask {

  val ClusterShardingTaskStrBase = this.getClass.getName.replace("$", "").split("\\.").last

  val ClusterShardingTaskTypeKey: EntityTypeKey[TaskProtocol] =
    EntityTypeKey[TaskProtocol](ClusterShardingTaskStrBase)

  /*
  def create(task: Task)(implicit clusterExt: ClusterExtension): ActorRef[ShardingEnvelope[TaskBehaviorProtocol]] = {
    val shard = clusterExt.shard
    shard.init(
      Entity(
        typeKey = ClusterShardingTaskTypeKey,
        createBehavior = _ => pure(task)
      )
    )
  }
 */
}
