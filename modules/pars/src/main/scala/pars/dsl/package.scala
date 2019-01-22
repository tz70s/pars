package pars

import cats.Monad
import fs2.Stream
import cats.implicits._

package object dsl {

  type ParsS[F[_], A] = Pars[F, Unit, A]

  // Instances for Pars

  implicit def parsMonadInstance[F[_]](implicit pe: ParEffect[F]) = new Monad[Pars[F, Unit, ?]] {

    override def pure[A](x: A): Pars[F, Unit, A] = Pars.emit(x)

    // TODO: correctly implement this.
    override def flatMap[A, B](fa: Pars[F, Unit, A])(f: A => Pars[F, Unit, B]): Pars[F, Unit, B] = {
      // Implicitly generate channel instance.
      val channel = Channel[A]("SystemGenerated")

      // Allocation and inject fa workload into channel
      val source = pe.server.allocate(fa, fa.strategy) *> channel.pub[F, A](fa.evaluateToStream)

      val inter = Pars.concat(channel) { from: Stream[F, A] =>
        from.map(f).map(_.evaluateToStream).flatMap(s => s)
      }

      inter.asInstanceOf[Pars[F, Unit, B]]
    }

    override def tailRecM[A, B](a: A)(f: A => Pars[F, Unit, Either[A, B]]): Pars[F, Unit, B] = ???
  }
}
