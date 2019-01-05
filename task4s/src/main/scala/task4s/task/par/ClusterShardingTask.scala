package task4s.task.par

import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import task4s.task.TaskProtocol.TaskProtocol

object ClusterShardingTask {

  val ClusterShardingTaskStrBase: String = this.getClass.getName.replace("$", "").split("\\.").last

  val ClusterShardingTaskTypeKey: EntityTypeKey[TaskProtocol] =
    EntityTypeKey[TaskProtocol](ClusterShardingTaskStrBase)
}
