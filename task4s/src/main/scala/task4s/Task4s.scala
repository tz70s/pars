package task4s
import task4s.task.TaskEnv

object Task4s {

  /**
   * Initiate a task environment.
   * A common practice is assigned the return task environment as implicit,
   * that can be passed around with internal tasks usage.
   *
   * Note that the name should be same in all cluster nodes, as this will propagate as actor system name.
   *
   * {{
   *   implicit val env = Task4s.env("example-tasks")
   * }}
   *
   */
  def env(name: String) = TaskEnv(name)

  /**
   * Spawn the task system.
   */
  def spawn(name: String): Unit = {}
}
