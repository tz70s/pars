package task4s.task

import akka.NotUsed

import scala.util.Try

/**
 * Domain event for monitoring task.
 */
sealed trait TaskEvent

/**
 * Coarse grained lifecycle event of a specific task.
 */
sealed trait TaskLifecycleEvent extends TaskEvent

case object TaskSpawned extends TaskLifecycleEvent

case class TaskDone(done: Try[NotUsed]) extends TaskLifecycleEvent

/**
 * Monitoring metrics event.
 */
sealed trait TaskMetricsEvent extends TaskEvent
