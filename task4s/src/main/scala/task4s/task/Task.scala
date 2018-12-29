package task4s.task

import akka.NotUsed
import akka.stream.scaladsl.RunnableGraph
import task4s.task.Task.ShapeBuilder

class Task private (private val shape: ShapeBuilder)(private implicit val stage: TaskStage) {

  private implicit val mat = stage.materializer

  /**
   * The initialization of shape will be constructed here,
   * therefore, any value used in task will be initialized once task factory method being get called.
   */
  private val runnable = shape(stage)

  /**
   * Evaluate internal runnable graph, but no materialized value get returned.
   */
  private[task4s] def eval(): NotUsed = {
    val runnable = shape(stage)

    // This is expected as unbounded stream that the materialized value will never return.
    runnable.run()
  }

  private[task4s] def stop(): Unit = {
    // May perform some required clean up here.
  }
}

private[task4s] object Task {

  private type ShapeBuilder = TaskStage => RunnableGraph[NotUsed]

  /**
   * Create an arbitrary task by passing shape builder closure.
   */
  private[task4s] def create(shape: ShapeBuilder)(implicit taskEnv: TaskStage) = new Task(shape)
}
