package task4s.task

import cats.effect.IO
import fs2.Stream
import fs2.concurrent.Topic

object WordCountStream {

  val topic = Topic("WordCountStream")

  def apply: Stream[IO, Unit] = topic.subscribe

}
