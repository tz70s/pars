package example

import akka.stream.scaladsl.{Sink, Source}
import task4s.task.{Task, TaskStage}

object WordCountApp {

  def main(args: Array[String]): Unit = {
    implicit val stage = TaskStage("WordCountApp")

    val task = Task.local() { implicit stage =>
      Source(0 to 10).to(Sink.foreach(println(_)))
    }

    Task.spawn(task)
  }
}
