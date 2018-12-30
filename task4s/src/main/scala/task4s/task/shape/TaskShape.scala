package task4s.task.shape

import akka.NotUsed
import akka.stream.scaladsl.RunnableGraph
import task4s.task.TaskStage

private[task4s] object TaskShape {
  type ShapeBuilder = TaskStage => RunnableGraph[NotUsed]
}
