package example

import akka.stream.scaladsl.{Keep, Sink, Source}
import task4s.task.{Task, TaskStage}

object WordCountTask {

  val TextString =
    """
      |To construct the computation, the basic primitive is Task, which is analog to a computation instance, but with distributed capabilities.
      |
      |The Task instance has following properties:
      |
      |Stateless.
      |Serving like a function with topic-based pub/sub model, with one binding for specific topic.
      |Data coming from different topic served as columnar data for analytics workload.
      |Can be distinguished for two types, LocalTask and ClusterTask, which refer to capability of executing transparently on cluster or not.
    """.stripMargin

  val MaximumDistinctWords = 4096

  implicit val stage = TaskStage("WordCountApp")

  val task = Task.cluster("WordCount") { implicit stage =>
    Source
      .single(TextString)
      .mapConcat(text => text.split("\n").toList)
      .map(line => line.stripMargin)
      .mapConcat(line => line.split(" ").toList)
      .groupBy(MaximumDistinctWords, identity)
      .map(_ -> 1)
      .reduce((l, r) => (l._1, l._2 + r._2))
      .mergeSubstreams
      .toMat(Sink.seq)(Keep.right)
  }
}
