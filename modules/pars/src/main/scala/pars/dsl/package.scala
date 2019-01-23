package pars

import cats.Monad
import fs2.Stream

package object dsl {

  implicit class StreamToParsM[F[_], +Out](val stream: Stream[F, Out]) {
    def pars(implicit pe: ParEffect[F]) = Pars(stream.covary[F])
  }

  // Instances for Pars

  implicit def parsMonadInstance[F[_]](implicit pe: ParEffect[F]) = new Monad[ParsM[F, ?]] {

    override def pure[A](x: A): ParsM[F, A] = Pars.emit(x)

    override def flatMap[A, B](fa: ParsM[F, A])(f: A => ParsM[F, B]): ParsM[F, B] = {
      // Implicitly generate channel instance.
      val channel = Channel[A](s"FlatMap${fa.channel.id}ImplicitChannel")

      val source = channel.pub(fa.evaluateToStream)

      Pars(fa.evaluateToStream.map(f).map(_.evaluateToStream).flatMap(s => s))
    }

    override def tailRecM[A, B](a: A)(f: A => ParsM[F, Either[A, B]]): ParsM[F, B] = ???
  }
}
