package example

import cats.kernel.Monoid

case class TaskSpawnRecord(success: Long = 0L, error: Long = 0L)

object TaskSpawnRecord {
  def succ = TaskSpawnRecord(1)
  def err = TaskSpawnRecord(0, 1)

  implicit val taskSpawnRecordMonoid: Monoid[TaskSpawnRecord] = new Monoid[TaskSpawnRecord] {
    override def combine(x: TaskSpawnRecord, y: TaskSpawnRecord): TaskSpawnRecord =
      TaskSpawnRecord(x.success + y.success, x.error + y.error)

    override def empty: TaskSpawnRecord = TaskSpawnRecord(0, 0)
  }
}
