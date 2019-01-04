package example

import cats.effect.{ExitCode, IO, IOApp}

object PureWorkerApp extends IOApp {

  import WordCountTask._

  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- IO { println(TextString) }
      _ <- IO.never
    } yield ExitCode.Success
}
