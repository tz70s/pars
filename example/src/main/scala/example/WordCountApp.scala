package example

import cats.effect.{ContextShift, ExitCode, IO, IOApp}
import task4s.task.{Task, TaskStage}
import cats.implicits._
import cats.kernel.{Monoid, Semigroup}

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

  case class TaskParResult(success: Long = 0L, error: Long = 0L)

  object TaskParResult {
    def succ = TaskParResult(1)
    def err = TaskParResult(0, 1)

    implicit val monoid = new Monoid[TaskParResult] {
      override def combine(x: TaskParResult, y: TaskParResult): TaskParResult =
        TaskParResult(x.success + y.success, x.error + y.error)

      override def empty: TaskParResult = TaskParResult(0, 0)
    }
  }

  import TaskParResult._

  def retryTaskWithBackOff(initialDelay: FiniteDuration,
                           maxRetries: Int)(implicit stage: TaskStage): IO[Seq[(String, Int)]] = {
    // TODO: may be we can ensure that the materialized value is already a future and remove this boilerplate.
    val ioa = Task.spawn(task).flatMap(f => IO.fromFuture(IO.pure(f)))
    ioa.handleErrorWith { error =>
      if (maxRetries > 0) {
        IO.sleep(initialDelay) *> retryTaskWithBackOff(initialDelay * 2, maxRetries - 1)
      } else
        IO.raiseError(error)
    }
  }

  def loop(times: Int): IO[TaskParResult] =
    IO.suspend {
      if (times > 0) {
        val backoff = retryTaskWithBackOff(300.millis, 5)
          .map(_ => succ)
          .handleError(_ => err)

        val eval = (backoff, loop(times - 1)).parMapN((l, r) => l |+| r)

        if (times % 100 == 0) contextShift.shift *> eval else eval
      } else IO.pure(Monoid.empty[TaskParResult])
    }

  override def run(args: List[String]): IO[ExitCode] =
    for {
      result <- loop(10000)
      _ <- IO { println(s"Complete word count task: success ${result.success}, error ${result.error}") }
    } yield ExitCode.Success
}
