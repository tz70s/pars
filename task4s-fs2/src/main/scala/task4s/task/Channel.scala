package task4s.task

import cats.Monoid
import cats.effect.Concurrent
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}

/** Specific type-based channel. */
case class Channel[F[_]: Concurrent, T: Monoid](name: String) {

  val MaxQueueSize = 16

  private val topic = {
    val t = implicitly[Monoid[T]]
    Topic[F, T](t.empty)
  }

  def pub(pubF: Stream[F, T])(implicit F: Concurrent[F]): Stream[F, Unit] =
    Stream.eval(topic).flatMap(t => t.publish(pubF))

  def sub(subF: Pipe[F, T, Unit])(implicit F: Concurrent[F]): Stream[F, Unit] =
    Stream.eval(topic).flatMap(t => t.subscribe(16)).through(subF)
}
