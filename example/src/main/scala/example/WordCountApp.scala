package example

import akka.stream.scaladsl.{Keep, Sink, Source}
import task4s.task.{Task, TaskStage}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
 * The infamous word count example.
 *
 * The word count style stream building via Akka stream can be found at:
 * [[https://doc.akka.io/docs/akka/2.5/stream/stream-cookbook.html#implementing-reduce-by-key]]
 */
object WordCountApp {

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

  def main(args: Array[String]): Unit = {
    implicit val stage = TaskStage("WordCountApp")
    implicit val ec = stage.system.executionContext

    val task = Task.local { implicit stage =>
      Source
        .single(TextString)
        .mapConcat(text => text.split(" ").toList)
        .groupBy(MaximumDistinctWords, identity)
        .map(_ -> 1)
        .reduce((l, r) => (l._1, l._2 + r._2))
        .mergeSubstreams
        .toMat(Sink.seq)(Keep.right)
    }

    // TODO: may be we can ensure that the materialized value is already a future and remove this boilerplate.
    val future: Future[Seq[(String, Int)]] = Task.spawn(task).flatMap(f => f)

    val result = Await.result(future, 1.second)

    println(result)

    Await.ready(stage.terminate(), 3.seconds)
  }
}
