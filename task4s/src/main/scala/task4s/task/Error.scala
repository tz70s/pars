package task4s.task

/**
 * Indicate the task is not founded during stage lookup.
 *
 * The potential problem may be caused by race condition.
 *
 * @param message Error message.
 * @param cause Internal cause, suppress by default.
 */
case class TaskNotFoundError(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

/**
 * Indicate the task staging error, the '''error''' means that this is an unrecoverable fatal error,
 * should be manually checked out and fix this.
 *
 * @param message Error message.
 * @param cause Internal cause, suppress by default.
 */
case class TaskStageError(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
