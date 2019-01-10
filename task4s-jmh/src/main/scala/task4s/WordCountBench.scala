package task4s

import cats.effect.{ContextShift, IO, Timer}
import task4s.task.{Task, TaskStage}
import cats.implicits._

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import example.{TaskSpawnRecord, WordCountTask}
import org.openjdk.jmh.annotations._

import scala.concurrent.{Await, Future}

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Threads(1)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
class WordCountBench {

  import TaskSpawnRecord._

  val NrOfRuns = 10000L

  val conf: Config =
    ConfigFactory.parseString("""akka.remote.netty.tcp.port = 2551""".stripMargin).withFallback(ConfigFactory.load())

  implicit val stage: TaskStage = TaskStage("WordCountApp", conf)

  sys.addShutdownHook { Await.ready(stage.terminate(), 3.second) }

  val tasks = new WordCountTask

  type WordCountTaskTpe = Task[Future[Seq[(String, Int)]]]

  implicit val contextShift: ContextShift[IO] = IO.contextShift(stage.system.executionContext)
  implicit val timer: Timer[IO] = IO.timer(stage.system.executionContext)

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

  @Benchmark
  def syncRunSingleTask(): Seq[(String, Int)] =
    retryTaskWithBackOff(tasks.singleTask, 300.millis, 5).unsafeRunSync()

  @Benchmark
  def syncRunReplicatedTask(): Seq[(String, Int)] =
    retryTaskWithBackOff(tasks.replicatedTasks, 300.millis, 5).unsafeRunSync()

  @Benchmark
  def parLoopWithSingleTask(): TaskSpawnRecord =
    loop(tasks.singleTask, NrOfRuns).unsafeRunSync()

  @Benchmark
  def parLoopWithReplicatedTask(): TaskSpawnRecord =
    loop(tasks.replicatedTasks, NrOfRuns).unsafeRunSync()

  // This is extremely slow, suppress benchmarking this; open it if required for concurrency test.
  def seqLoopWithSingleTask(): TaskSpawnRecord =
    loopSeq(tasks.singleTask, NrOfRuns).unsafeRunSync()

  @TearDown
  def shutdown(): Unit = {
    val _ = Await.result(stage.terminate(), 3.second)
  }
}
