package task4s.remote.serialize

import fs2.Stream

private[task4s] case class SerializableStreamT[F[_], A](ev: Stream[F, A])

/**
 * Standard serialization wrapper for stream, the return value is unit.
 * You can use stream hierarchy to compose a larger stream, then the serialized computation will be effectively evaluated.
 *
 * @param ev Stream instance.
 * @tparam F Context type.
 */
private[task4s] case class SerializableStreamF[F[_]](ev: Stream[F, Unit])
