package task4s.task

import akka.stream.scaladsl.Flow

/**
 * TaskShape represents the internal dataflow of a Task.
 *
 * To use this, the dataflow should be constructed in a single producer and single consumer flow.
 *
 * {{
 * val task = Task4s.create[TextData]("text") { ctx =>
 *   val flow = Flow[TextData]
 *     .flatMap(line => line.split(""))
 *     .map(word => (word, 1))
 *     .reduceByKey(_ + _)
 *   TaskShape(flow)
 * }
 * }}
 */
class TaskShape[-In, +Out, +Mat] private (val flow: Flow[In, Out, Mat])

object TaskShape {
  def apply[In, Out, Mat](flow: Flow[In, Out, Mat]): TaskShape[In, Out, Mat] = new TaskShape(flow)
}
