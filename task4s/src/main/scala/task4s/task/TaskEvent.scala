package task4s.task

import akka.NotUsed

import scala.concurrent.Future

/**
 * Domain event for monitoring task.
 */
sealed trait TaskEvent

/**
 * Coarse grained lifecycle event of a specific task.
 */
sealed trait TaskLifecycleEvent extends TaskEvent

case object TaskSpawned extends TaskLifecycleEvent

case class TaskDown(done: Future[NotUsed]) extends TaskLifecycleEvent

/**
 * Metrics monitoring event.
 */
sealed trait TaskMetricsEvent extends TaskEvent
