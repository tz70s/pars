package pars.cluster.internal

import cats.effect.{Concurrent, ContextShift, Timer}
import fs2.concurrent.Queue
import pars.internal.UnsafePars
import pars.internal.remote.tcp.TcpSocketConfig

class StateBackend[F[_]: Concurrent: ContextShift: Timer](val workerStates: Queue[F, WorkerState],
                                                          val records: Queue[F, ParsRecords[F]])

case class WorkerState(address: TcpSocketConfig, lastLive: Long)

case class ParsRecords[F[_]](pars: UnsafePars[F], records: Set[TcpSocketConfig])
