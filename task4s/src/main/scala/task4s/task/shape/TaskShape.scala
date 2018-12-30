package task4s.task.shape

import akka.stream.scaladsl.RunnableGraph
import task4s.task.TaskStage

private[task4s] object TaskShape {
  type ShapeBuilder[+Mat] = TaskStage => RunnableGraph[Mat]
}
