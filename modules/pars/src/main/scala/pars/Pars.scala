package pars

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import fs2.concurrent.Queue
import fs2.{Pure, RaiseThrowable, Stream}
import pars.cluster.Coordinator

/**
 * Core abstraction over serializable stream closure for distributed computation.
 *
 * Pars provides multiple factory methods which similar to [[fs2.Stream]] with some extension for remote evaluation.
 *
 * Note that you should carefully ensure that the closure serializable when factory pars via flying methods,
 * i.e. [[pars.Pars.concat]], [[pars.Pars.offload]].
 *
 * By applying these factories method, the environment enclosed into closure should be also serializable,
 * or it will incur a runtime error when serialization.
 *
 * Some common pitfall you'll have to make sure is calling '''this''' of surrounding class.
 *
 * @example {{{
 * val n = new NotSerializable()
 *
 * val m = Pars.offload {
 *   Stream.emit(n)
 * }
 * }}}
 *
 * It will be nice if we have serializable type constraint, i.e. Spores.
 *
 * However, you can find out the serialization path for debugging now by extending JVM serialization debug info.
 * @example {{{
 * $ sbt -Dsun.io.serialization.extendedDebugInfo=true test
 * }}}
 */
class Pars[F[_], -In, +Out] private[pars] (
    val process: Stream[F, In] => Stream[F, Out],
    val in: Channel[_ >: In],
    val out: Channel[Out],
    val strategy: Strategy = Strategy(1)
)(implicit val ev: ParEffect[F])
    extends Serializable {

  private[pars] def evaluateToStream(in: Stream[F, In]): Stream[F, Out] = process(in)

  private[pars] def evaluateToStream: Stream[F, Out] = process(Stream.empty)
}

class ParsM[F[_], +Out] private[pars] (process: Stream[F, Out],
                                       in: Channel[Unit],
                                       out: Channel[Out],
                                       strategy: Strategy = Strategy(1))(
    implicit ev: ParEffect[F]
) extends Pars[F, Unit, Out](_ => process, in, out, strategy) {}

object Pars {

  /**
   * Pure value emission for pars, alias to fs2.Stream.apply.
   *
   * @see fs2.Stream.apply
   * @param values Varargs of application values.
   */
  def apply[F[_], Out](values: Out*)(implicit ev: ParEffect[F]): ParsM[F, Out] =
    supplyStream(Stream(values: _*).covary[F])

  /**
   * Pure value emission for pars, alias to fs2.Stream.emit.
   *
   * @see fs2.Stream.emit
   * @param value Emits single value.
   */
  def emit[F[_], Out](value: Out)(implicit ev: ParEffect[F]): ParsM[F, Out] =
    supplyStream(Stream.emit(value))

  /**
   * Pure value emission for pars, alias to fs2.Stream.emits.
   *
   * @see fs2.Stream.emits
   * @param values Emits sequence values.
   */
  def emits[F[_], Out](values: Seq[Out])(implicit ev: ParEffect[F]): ParsM[F, Out] =
    supplyStream(Stream.emits(values))

  /**
   * Effective evaluation for pars.
   *
   * @example {{{
   * def m[F[_]: Sync] = Pars {
   *   for {
   *     s <- Stream(1, 2, 3)
   *     p <- Stream.eval(Sync[F].delay(println(s))
   *   } yield p
   * }
   * }}}
   *
   * @param stream The evaluated Stream.
   */
  def apply[F[_], Out](stream: Stream[F, Out])(implicit ev: ParEffect[F]): ParsM[F, Out] =
    supplyStream(stream)

  def apply[F[_], Out](out: Channel[Out])(stream: Stream[F, Out])(implicit ev: ParEffect[F]): ParsM[F, Out] =
    new ParsM[F, Out](stream, Channel(s"system-generate${stream.hashCode()}-${ev.coordinators}-in"), out)

  private def supplyStream[F[_], Out](stream: Stream[F, Out])(implicit ev: ParEffect[F]): ParsM[F, Out] =
    new ParsM[F, Out](stream,
                      Channel(s"system-generate${stream.hashCode()}-${ev.coordinators}-in"),
                      Channel(s"system-generate${stream.hashCode()}-${ev.coordinators}-out"))

  /**
   * Concatenate to a specified channel.
   *
   * @example {{{
   * val m = Pars.concat(Channel("SomeNumbers")) { s =>
   *  s.map(_ + 1)
   * }
   * }}}
   *
   * @param in Input channel to evaluated Stream.
   * @param out Output channel after evaluation.
   * @param process The pipe process evaluation from a stream to another stream.
   */
  def concat[F[_], In, Out](
      in: Channel[In],
      out: Channel[Out],
      strategy: Strategy = Strategy(1)
  )(process: Stream[F, In] => Stream[F, Out])(implicit ev: ParEffect[F]) =
    new Pars[F, In, Out](process, in, out, strategy)

  /**
   * Perform offloading, which takes one system implicit generated channel.
   *
   * @example {{{
   * val m = Pars.offload {
   *   for {
   *     s <- Stream(1, 2, 3)
   *     p <- Stream.eval(Sync[F].delay(println(s))
   *   } yield p
   * }
   * }}}
   * @param stream The evaluated Stream.
   */
  def offload[F[_], Out](stream: Stream[F, Out])(implicit ev: ParEffect[F]): Pars[F, Unit, Out] =
    supplyStream(stream)

  /**
   * Spawn a pars on the fly.
   *
   * @example {{{
   * Pars.spawn(pars).flatMap { (in, out) => ... }
   * }}}
   *
   * @param pars Pars for spawned.
   * @return Stream of evaluated with channel.
   */
  def spawn[F[_]: Concurrent: ContextShift: Timer: RaiseThrowable, I, O](pars: Pars[F, I, O]): Stream[F, Channel[I]] =
    pars.ev.spawn(pars)

  /**
   * Serve a ParsM by spawning and trigger it.
   *
   * @param pars A ParsM instance.
   * @return Evaluated stream.
   */
  def serveM[F[_]: Concurrent: ContextShift: Timer: RaiseThrowable, I, O](pars: ParsM[F, O]): Stream[F, Unit] = {
    implicit val ev = pars.ev
    ev.spawn(pars).flatMap(c => c.unit())
  }

  /**
   * Service all logic in place.
   *
   * @example {{{
   * val stream = Pars.service(f)(pe)(coordinators)
   * }}}
   *
   * @param f Core logic of streams.
   * @param pes ParEffect instances.
   * @param coordinators Coordinators instances.
   * @return Stream of core logic which back internal server and coordinator running in background.
   */
  def service[F[_]: Concurrent, T](f: Stream[F, T])(pes: ParEffect[F]*)(coordinators: Coordinator[F]*): Stream[F, T] = {
    val bindServers = Stream.emits(pes).map(p => p.server.bindAndHandle).parJoinUnbounded
    val bindCoordinators = Stream.emits(coordinators).map(c => c.bindAndHandle()).parJoinUnbounded

    f concurrently bindServers concurrently bindCoordinators
  }

  /**
   * Alternative builder pattern for service all logic in place.
   *
   * @example {{{
   * val stream = Pars.bind(s).bind(pe).bind(coordinator).build
   * }}}
   *
   * @param f Core logic of streams.
   */
  def bind[F[_]: Concurrent, T](f: Stream[F, T]) = new ParsServiceBuilder[F, T](f)
}

private[pars] class ParsServiceBuilder[F[_]: Concurrent, T](f: Stream[F, T]) {

  var pes: Seq[ParEffect[F]] = Seq.empty
  var coordinators: Seq[Coordinator[F]] = Seq.empty

  def bind(p: ParEffect[F], ps: ParEffect[F]*): ParsServiceBuilder[F, T] = {
    pes = pes ++ Seq(p) ++ ps
    this
  }

  def bind(c: Coordinator[F], cs: Coordinator[F]*): ParsServiceBuilder[F, T] = {
    coordinators = coordinators ++ Seq(c) ++ cs
    this
  }

  def build: Stream[F, T] = Pars.service(f)(pes: _*)(coordinators: _*)
}
