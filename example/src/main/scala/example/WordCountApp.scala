package example

import cats.effect.{ExitCode, IO, IOApp}
import task4s.task.Task
import cats.implicits._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

/**
 * The infamous word count example.
 *
 * The word count style stream building via Akka stream can be found at:
 * [[https://doc.akka.io/docs/akka/2.5/stream/stream-cookbook.html#implementing-reduce-by-key]]
 */
object WordCountApp extends IOApp {

  import WordCountTask._

  import TaskSpawnRecord._

  type WordCountTaskTpe = Task[Future[Seq[(String, Int)]]]

  val NrOfRuns = 1000000L

  def retryTaskWithBackOff(task: WordCountTaskTpe,
                           initialDelay: FiniteDuration,
                           maxRetries: Int): IO[Seq[(String, Int)]] = {
    // TODO: may be we can ensure that the materialized value is already a future and remove this boilerplate.
    val ioa = Task.spawn(task).flatMap(f => IO.fromFuture(IO.pure(f)))
    ioa.handleErrorWith { error =>
      if (maxRetries > 0) {
        IO.sleep(initialDelay) *> retryTaskWithBackOff(task, initialDelay * 2, maxRetries - 1)
      } else
        IO.raiseError(error)
    }
  }

  def loop(task: WordCountTaskTpe, times: Long): IO[TaskSpawnRecord] = {
    val backOff = retryTaskWithBackOff(task, 300.millis, 5)
      .flatMap(wcs => IO { println(wcs); wcs })
      .map(_ => succ)
      .handleError(_ => err)

    // Note that the parSequence canceled tasks if any task got failed, however, we've recovered all failure case into error record.
    (1L to times).map(_ => backOff).toList.parSequence.flatMap(list => IO { list.reduce(_ |+| _) })
  }

  def loopSeq(task: WordCountTaskTpe, times: Long): IO[TaskSpawnRecord] =
    if (times > 0) {
      val backOff = retryTaskWithBackOff(task, 300.millis, 5)
        .map(_ => succ)
        .handleError(_ => err)

      backOff.flatMap { l =>
        loopSeq(task, times - 1).map(r => r |+| l)
      }
    } else IO.pure(TaskSpawnRecord())

  override def run(args: List[String]): IO[ExitCode] =
    for {
      result <- loop(singleTask, NrOfRuns)
      _ <- IO { println(s"Complete word count task: success ${result.success}, error ${result.error}") }
    } yield ExitCode.Success
}
