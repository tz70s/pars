package task4s.task

import java.nio.file.{NoSuchFileException, Paths}
import java.util.concurrent.Executors

import cats.effect._
import fs2.Stream
import fs2.io.file._
import fs2.text
import cats.implicits._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Main extends IOApp {

  val blockingExecutor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(8))

  def splitter[F[_]: Sync: ContextShift]: Stream[F, String] = {
    val content = readAll[F](Paths.get("text.txt"), blockingExecutor, 16)
    content.through(text.utf8Decode).through(text.lines)
  }

  def fileNotFound[F[_]: Sync](throwable: Throwable): Stream[F, Unit] =
    Stream.eval(Sync[F].delay(println(s"Can't get the given file: ${throwable.getMessage}")))

  def printFileContent[F[_]: Sync: ContextShift]: Stream[F, Unit] = {
    val showOffContent = for {
      content <- splitter
      _ <- Stream.eval(Sync[F].delay { println(content) })
    } yield ()

    showOffContent.handleErrorWith { case t: NoSuchFileException => fileNotFound[F](t) }
  }

  def run(args: List[String]): IO[ExitCode] =
    printFileContent[IO].compile.drain.as(ExitCode.Success)
}
