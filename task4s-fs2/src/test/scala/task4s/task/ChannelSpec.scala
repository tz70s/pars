package task4s.task

import cats.effect.IO
import org.scalatest.WordSpec
import cats.implicits._
import fs2.Stream

import scala.concurrent.ExecutionContext

class ChannelSpec extends WordSpec {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  "Channel" should {

    "construct singleton topic" in {
      val channel = Channel[IO, Int]("channel-1")

      val pubS = channel.pub(Stream.range(1, 100).covary[IO])
      val subS = channel.sub(s => s.flatMap(value => Stream.eval(IO { println(value + 1) })))

      val s = Stream(pubS.concurrently(subS)).parJoinUnbounded
      s.compile.drain.unsafeRunSync()
    }
  }
}
