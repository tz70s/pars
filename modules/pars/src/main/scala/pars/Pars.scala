package pars

import cats.effect.{Concurrent, ContextShift, Timer}
import fs2.{RaiseThrowable, Stream}

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
    val channel: Channel[_ >: In],
    val strategy: Strategy = Strategy(1)
)(implicit val ev: ParEffect[F])
    extends Serializable {

  private[pars] def evaluateToStream(in: Stream[F, In]): Stream[F, Out] = process(in)

  private[pars] def evaluateToStream: Stream[F, Out] = process(Stream.empty)
}

class ParsM[F[_], +Out] private[pars] (process: Stream[F, Out],
                                       channel: Channel[Unit],
                                       strategy: Strategy = Strategy(1))(
    implicit ev: ParEffect[F]
) extends Pars[F, Unit, Out](_ => process, channel, strategy) {}

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

  private def supplyStream[F[_], Out](stream: Stream[F, Out])(implicit ev: ParEffect[F]): ParsM[F, Out] =
    new ParsM[F, Out](stream, Channel(s"SystemGenerate${stream.hashCode()}-${ev.coordinators}"))

  /**
   * Concatenate to a specified channel.
   *
   * @example {{{
   * val m = Pars.concat(Channel("SomeNumbers")) { s =>
   *  s.map(_ + 1)
   * }
   * }}}
   *
   * @param channel Input channel to evaluated Stream.
   * @param process The pipe process evaluation from a stream to another stream.
   */
  def concat[F[_], In, Out](
      channel: Channel[In]
  )(process: Stream[F, In] => Stream[F, Out])(implicit ev: ParEffect[F]) =
    new Pars[F, In, Out](process, channel)

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

  def bind[F[_]: Concurrent: ContextShift: Timer: RaiseThrowable, I, O](pars: Pars[F, I, O]): Stream[F, Channel[I]] = {
    val pe = pars.ev
    pe.spawn(pars).concurrently(pe.server.bindAndHandle)
  }
}
