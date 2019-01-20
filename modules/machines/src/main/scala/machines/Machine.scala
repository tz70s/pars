package machines

import fs2.Stream

import scala.reflect.ClassTag

/**
 * Core abstraction over serializable stream closure for distributed computation.
 *
 * Machine provides multiple factory methods which similar to [[fs2.Stream]] with some extension for remote evaluation.
 *
 * Note that you should carefully ensure that the closure serializable when factory machine via flying methods,
 * i.e. [[machines.Machine.concat]], [[machines.Machine.offload]].
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
 * @example {{{
 * $ sbt -Dsun.io.serialization.extendedDebugInfo=true test
 * }}}
 */
abstract class Machine[F[_], -In, +Out] private[machines] (val process: Stream[F, In] => Stream[F, Out])(
    @transient implicit val ev: ParEffect[F]
) extends Serializable {

  private[machines] def evaluateToStream(in: Stream[F, In]): Stream[F, Out] = process(in)

  private[machines] def evaluateToStream: Stream[F, Out] = process(Stream.empty)
}

/**
 * Represented as Machine with no input channel.
 */
private[machines] class MachineSource[F[_], +Out](process: Stream[F, Out])(implicit ev: ParEffect[F])
    extends Machine((_: Stream[F, Unit]) => process)

/**
 * Represented as machine which can be cluster-wide allocated.
 */
private[machines] class FlyingMachine[F[_], -In, +Out](process: Stream[F, In] => Stream[F, Out],
                                                       val channel: Channel[_ >: In])(implicit ev: ParEffect[F])
    extends Machine[F, In, Out](process)

/**
 * Represented as Machine with no input channel and cluster-wide allocated.
 */
private[machines] class FlyingMachineSource[F[_], +Out](process: Stream[F, Out])(implicit ev: ParEffect[F])
    extends FlyingMachine((_: Stream[F, Unit]) => process, Channel("SystemGeneratedUID"))

object Machine {

  /**
   * Pure value emission for machine, alias to fs2.Stream.apply.
   *
   * @see fs2.Stream.apply
   * @param values Varargs of application values.
   */
  def apply[F[_], Out](values: Out*)(implicit ev: ParEffect[F]): MachineSource[F, Out] =
    new MachineSource[F, Out](Stream(values: _*))

  /**
   * Pure value emission for machine, alias to fs2.Stream.emit.
   *
   * @see fs2.Stream.emit
   * @param value Emits single value.
   */
  def emit[F[_], Out](value: Out)(implicit ev: ParEffect[F]): MachineSource[F, Out] =
    new MachineSource[F, Out](Stream.emit(value))

  /**
   * Pure value emission for machine, alias to fs2.Stream.emits.
   *
   * @see fs2.Stream.emits
   * @param values Emits sequence values.
   */
  def emits[F[_], Out](values: Seq[Out])(implicit ev: ParEffect[F]): MachineSource[F, Out] =
    new MachineSource[F, Out](Stream.emits(values))

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
   */
  def apply[F[_], Out](stream: Stream[F, Out])(implicit ev: ParEffect[F]): MachineSource[F, Out] =
    new MachineSource[F, Out](stream)

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
  def concat[F[_], In: ClassTag, Out: ClassTag](
      channel: Channel[In]
  )(process: Stream[F, In] => Stream[F, Out])(implicit ev: ParEffect[F]) =
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
  def offload[F[_], Out: ClassTag](stream: Stream[F, Out])(implicit ev: ParEffect[F]) =
    new FlyingMachineSource[F, Out](stream)
}
