package task4s

import task4s.task.{Task, TaskStage}

object Task4s {

  /**
   * Initiate a [[task.TaskStage]].
   *
   * A common practice is assigned the return task stage as implicit,
   * which can be passed around with internal tasks usage.
   *
   * Note that the name should be same in all cluster nodes, as this will propagate as actor system name.
   *
   * {{{
   * implicit val stage = Task4s.stage("SampleTaskStage")
   * }}}
   *
   */
  def stage(name: String): TaskStage = TaskStage(name)

  /**
   * Java API.
   */
  def getStage(name: String): TaskStage = stage(name)
}
