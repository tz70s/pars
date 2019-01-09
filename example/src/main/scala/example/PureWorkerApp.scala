package example

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.apply._
import com.typesafe.config.ConfigFactory
import task4s.task.TaskStage

import scala.concurrent.Await
import scala.concurrent.duration._

object PureWorkerApp extends IOApp {

  val conf =
    ConfigFactory.parseString("""akka.remote.netty.tcp.port = 2551""".stripMargin).withFallback(ConfigFactory.load())

  implicit val stage: TaskStage = TaskStage("WordCountApp", conf)

  sys.addShutdownHook { Await.ready(stage.terminate(), 3.second) }

  val tasks = new WordCountTask

  override def run(args: List[String]): IO[ExitCode] =
    IO.never *> IO.pure(ExitCode.Success)
}
