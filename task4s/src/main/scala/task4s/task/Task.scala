package task4s.task

class Task[-In, +Out, +Mat] private (private val shape: TaskShape[In, Out, Mat])(implicit val taskEnv: TaskEnv) {}

/**
 * INTERNAL API.
 */
object Task {

  private type ShapeBuilder[-In, +Out, +Mat] = TaskEnv => TaskShape[In, Out, Mat]

  /**
   * Create an arbitrary task by passing shape builder closure.
   */
  private[task4s] def create[In, Out, Mat](shape: ShapeBuilder[In, Out, Mat])(implicit taskEnv: TaskEnv) =
    new Task(shape(taskEnv))

}
