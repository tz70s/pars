package task4s

import fs2.{Pure, Stream}

/**
 * Core abstraction over serializable stream closure for distributed computation.
 *
 * Machine provides multiple factory methods which similar to [[fs2.Stream]] with some extension for remote evaluation.
 *
 * Note that you should carefully ensure that the closure serializable when factory machine via flying methods,
 * i.e. [[task4s.Machine.concat]], [[task4s.Machine.offload]].
 *
 * By applying these factories method, the environment enclosed into closure should be also serializable,
 * or it will incur a runtime error when serialization.
 *
 * Some common pitfall you'll have to make sure is calling '''this''' of surrounding class.
 *
 * @example {{{
 * val n = new NotSerializable()
 *
 * val m = Machine.offload {
 *   Stream.emit(n)
 * }
 * }}}
 *
 * It will be nice if we have serializable type constraint, i.e. Spores.
 *
 * However, you can find out the serialization path for debugging now by extending JVM serialization debug info.
 *
 * @example {{{
 * $ sbt -Dsun.io.serialization.extendedDebugInfo=true test
 * }}}
 */
class Machine[F[_], -In, +Out] private[task4s] (val process: Stream[F, In] => Stream[F, Out]) extends Serializable {
  private[task4s] def assemble(in: Stream[F, In]): Stream[F, Out] = process(in)

  private[task4s] def assemble: Stream[F, Out] = process(Stream.empty)
}

/**
 * Represented as machine which can be cluster-wide allocated.
 */
private[task4s] class FlyingMachine[F[_], -In, +Out](process: Stream[F, In] => Stream[F, Out],
                                                     val channel: Channel[_ >: In])
    extends Machine[F, In, Out](process)

object Machine {

  /**
   * Pure value emission for machine, alias to fs2.Stream.apply.
   *
   * @see fs2.Stream.apply
   * @param values Varargs of application values.
   */
  def apply[F[x] >: Pure[x], Out](values: Out*) = new Machine[F, Unit, Out](_ => Stream(values: _*))

  /**
   * Pure value emission for machine, alias to fs2.Stream.emit.
   *
   * @see fs2.Stream.emit
   * @param value Emits single value.
   */
  def emit[F[x] >: Pure[x], Out](value: Out) = new Machine[F, Unit, Out](_ => Stream.emit(value))

  /**
   * Pure value emission for machine, alias to fs2.Stream.emits.
   *
   * @see fs2.Stream.emits
   * @param values Emits sequence values.
   */
  def emits[F[x] >: Pure[x], Out](values: Seq[Out]) = new Machine[F, Unit, Out](_ => Stream.emits(values))

  /**
   * Effective evaluation for machine.
   *
   * @example {{{
   * def m[F[_]: Sync] = Machine {
   *   for {
   *     s <- Stream(1, 2, 3)
   *     p <- Stream.eval(Sync[F].delay(println(s))
   *   } yield p
   * }
   * }}}
   *
   * @param stream The evaluated Stream.
   * @tparam F The effect context F-algebra.
   * @tparam Out Return type of evaluated stream.
   * @return Machine instance.
   */
  def apply[F[_], Out](stream: Stream[F, Out]): Machine[F, Unit, Out] = new Machine(_ => stream)

  /**
   * Concatenate to a specified channel.
   *
   * @example {{{
   * val m = Machine.concat(Channel("SomeNumbers")) { s =>
   *  s.map(_ + 1)
   * }
   * }}}
   *
   * @param channel Input channel to evaluated Stream.
   * @param process The pipe process evaluation from a stream to another stream.
   */
  def concat[F[_], In, Out](channel: Channel[In])(process: Stream[F, In] => Stream[F, Out]) =
    new FlyingMachine[F, In, Out](process, channel)

  /**
   * Perform offloading, which takes one system implicit generated channel.
   *
   * @example {{{
   * val m = Machine.offload {
   *   for {
   *     s <- Stream(1, 2, 3)
   *     p <- Stream.eval(Sync[F].delay(println(s))
   *   } yield p
   * }
   * }}}
   * @param stream The evaluated Stream.
   */
  def offload[F[_], Out](stream: Stream[F, Out]) =
    new FlyingMachine[F, Unit, Out](_ => stream, Channel("SystemGeneratedUID"))
}
