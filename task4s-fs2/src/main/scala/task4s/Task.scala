package task4s

import cats.MonadError
import fs2.Stream

final class Task[F[_], +T] private (val stream: Stream[F, Unit], val channel: Channel[F, T]) {
  def allocate(strategy: Strategy)(implicit allocator: Allocator): Stream[F, T] =
    allocator.allocate(this, strategy)
}

object Task {

  def liftT[F[_], T](stream: Stream[F, Unit], channel: Channel[F, T]): Unit = {}

  def liftT[F[_], T](stream: Stream[F, T]): Unit = {}

  implicit def monadErrorInstance[F[_]]: MonadError[Task[F, ?], Throwable] = new MonadError[Task[F, ?], Throwable] {

    def raiseError[A](e: Throwable): Task[F, A] = ???

    def handleErrorWith[A](fa: Task[F, A])(f: Throwable => Task[F, A]): Task[F, A] = ???

    def pure[A](x: A): Task[F, A] = ???

    def flatMap[A, B](fa: Task[F, A])(f: A => Task[F, B]): Task[F, B] = ???

    def tailRecM[A, B](a: A)(f: A => Task[F, Either[A, B]]): Task[F, B] = ???
  }
}
