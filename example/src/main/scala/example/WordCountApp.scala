package example

import cats.effect.{ExitCode, IO, IOApp}
import task4s.task.{Task, TaskStage}
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

/**
 * The infamous word count example.
 *
 * The word count style stream building via Akka stream can be found at:
 * [[https://doc.akka.io/docs/akka/2.5/stream/stream-cookbook.html#implementing-reduce-by-key]]
 */
object WordCountApp extends IOApp {

  val conf: Config =
    ConfigFactory.parseString("""akka.remote.netty.tcp.port = 2552""".stripMargin).withFallback(ConfigFactory.load())

  implicit val stage: TaskStage = TaskStage("WordCountApp", conf)

  sys.addShutdownHook { Await.ready(stage.terminate(), 3.second) }

  val tasks = new WordCountTask

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
        for {
          _ <- IO.sleep(initialDelay)
          _ <- IO { println(s"Start retry for task $task cause error ${error.getMessage}") }
          retry <- retryTaskWithBackOff(task, initialDelay * 2, maxRetries - 1)
        } yield retry
      } else
        IO.raiseError(error)
    }
  }

  def loop(task: WordCountTaskTpe, times: Long): IO[TaskSpawnRecord] = {
    val backOff = retryTaskWithBackOff(task, 300.millis, 5)
      .map(_ => succ)
      .handleErrorWith(ex => IO { println(ex.getMessage) } *> IO.pure(err))

    // Note that the parSequence canceled tasks if any task got failed, however, we've recovered all failure case into error record.
    (1L to times).map(_ => backOff).toList.parSequence.flatMap(list => IO { list.reduce(_ |+| _) })
  }

  def loopSeq(task: WordCountTaskTpe, times: Long): IO[TaskSpawnRecord] =
    if (times > 0) {
      val backOff = retryTaskWithBackOff(task, 300.millis, 5)
        .flatMap(wc => IO { println(wc) })
        .map(_ => succ)
        .handleErrorWith(ex => IO { println(ex.getMessage) } *> IO.pure(err))

      backOff.flatMap { l =>
        loopSeq(task, times - 1).map(r => r |+| l)
      }
    } else IO.pure(TaskSpawnRecord())

  override def run(args: List[String]): IO[ExitCode] =
    for {
      result <- loopSeq(tasks.singleTask, NrOfRuns)
      _ <- IO { println(s"Complete word count task: success ${result.success}, error ${result.error}") }
    } yield ExitCode.Success
}
