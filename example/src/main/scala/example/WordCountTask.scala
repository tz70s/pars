package example

import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import task4s.task.par.ParStrategy
import task4s.task.{Task, TaskStage}

import scala.collection.immutable
import scala.concurrent.Future

object WordCountTask {

  val MaximumDistinctWords = 4096

  def apply(implicit stage: TaskStage): WordCountTask = new WordCountTask

  val Poetry = """Humpty Dumpty sat on a wall,
                 |Humpty Dumpty had a great fall.
                 |All the king's horses and all the king's men
                 |Couldn't put Humpty together again."""
}

class WordCountTask(implicit stage: TaskStage) {
  import WordCountTask._

  val wordCount: TaskStage => RunnableGraph[
    Future[immutable.Seq[(String, Int)]]
  ] = { _: TaskStage =>
    Source
      .repeat(Poetry)
      .take(10000)
      .mapConcat(text => text.split("\n").toList)
      .map(line => line.stripMargin)
      .mapConcat(line => line.split(" ").toList)
      .groupBy(MaximumDistinctWords, identity)
      .map(_ -> 1)
      .reduce((l, r) => (l._1, l._2 + r._2))
      .mergeSubstreams
      .toMat(Sink.seq)(Keep.right)
  }

  val singleTask: Task[
    Future[immutable.Seq[(String, Int)]]
  ] = Task.cluster("WordCountSingle")(wordCount)

  val replicatedTasks: Task[
    Future[immutable.Seq[(String, Int)]]
  ] = Task.cluster("WordCountReplicated", ParStrategy(100, Set.empty))(wordCount)
}
