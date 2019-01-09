package task4s.remote.serialize

import fs2.Stream

case class SerializableStream[F[_], A](ev: Stream[F, A])
