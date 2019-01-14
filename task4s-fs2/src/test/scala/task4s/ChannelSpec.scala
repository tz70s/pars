package task4s

import cats.effect.IO
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext.Implicits.global
import fs2.Stream
import fs2.concurrent.Queue

class ChannelSpec extends WordSpec with Matchers {

  implicit val cs = IO.contextShift(global)

  "Channel" should {}

  "ChannelInternalQueue" should {

    "enqueue and dequeue by concurrently enqueue in background with none signaling" in {
      val expect = List(1, 2, 3)
      val queue = Queue.noneTerminated[IO, Int]

      // NOTE that the evaluation of queue should be careful on blocking behavior.
      // Or it will be deadlocked or blocked into never end.
      // This is a sample terminated queue.
      // Or see example: https://fs2.io/concurrency-primitives.html
      val result = for {
        q <- Stream.eval(queue)
        s <- q.dequeue concurrently q.enqueue(Stream.emits(expect.map(Some(_))) ++ Stream.emit(None))
      } yield s

      result.compile.toList.unsafeRunSync() shouldBe expect
    }
  }

}
