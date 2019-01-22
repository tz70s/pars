package machines.cluster.internal

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import fs2.{Pipe, RaiseThrowable, Stream}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import machines.cluster.CoordinationProtocol._
import machines.{Channel, FlyingMachine, Machine}
import machines.cluster.Coordinator
import machines.internal.Protocol.Protocol
import machines.internal.remote.NetService
import machines.internal.remote.tcp.TcpSocketConfig

import scala.collection.concurrent.TrieMap
import scala.util.Random
import cats.implicits._

import scala.concurrent.duration._

class StandAloneCoordinator[F[_]: Concurrent: ContextShift: Timer](
    implicit val acg: AsynchronousChannelGroup
) extends Coordinator {

  private val protocolF = new StandAloneCoordinatorProtocolF[F]

  def bindAndHandle(address: TcpSocketConfig): Stream[F, Unit] =
    NetService[F].bindAndHandle(address, protocolF.logic)
}

object StandAloneCoordinator {
  def apply[F[_]: Concurrent: ContextShift: Timer](implicit acg: AsynchronousChannelGroup): StandAloneCoordinator[F] =
    new StandAloneCoordinator[F]()
}

case class MachineExtension[F[_], -I, +O](machine: Machine[F, I, O], records: Set[TcpSocketConfig])

case class WorkerState(lastLive: Long)

class StandAloneCoordinatorProtocolF[F[_]: Concurrent: ContextShift: RaiseThrowable: Timer](
    implicit acg: AsynchronousChannelGroup
) {

  private implicit val log: Logger[F] = Slf4jLogger.unsafeCreate[F]

  private type UnsafeMachine = MachineExtension[F, _, _]

  private type UnsafeM = FlyingMachine[F, _, _]

  @volatile private var workers = Map[TcpSocketConfig, WorkerState]()

  private val repository = TrieMap[Channel[_], UnsafeMachine]()

  def logic: Pipe[F, Protocol, Protocol] = { from =>
    val stream = from.flatMap {
      case Ping(address) =>
        for {
          currentTime <- Stream.eval(Timer[F].clock.realTime(MILLISECONDS))
          _ <- Stream.eval(Logger[F].info(s"Update worker $address with timestamp $currentTime millis"))
          _ <- Stream.eval(Sync[F].delay(synchronized { workers += (address -> WorkerState(currentTime)) }))
          pong <- Stream.emit(Pong)
        } yield pong

      case AllocationRequest(machine, strategy) =>
        if (workers.isEmpty)
          Stream.emit(
            RequestErr(NoAvailableWorker(s"Coordinator can't find available worker, current workers: $workers"))
          )
        else {
          val replicas = if (strategy.replicas > workers.size) workers.size else strategy.replicas
          allocateToWorkers(replicas, machine.asInstanceOf[UnsafeM]).handleErrorWith { t =>
            Stream.eval(Logger[F].error(s"Allocation error, cause : $t")) *> Stream.emit(RequestErr(t))
          }
        }
    }

    stream.concurrently(checkWorkerOutliveOrNot())
  }

  private def allocateToWorkers(replicas: Int, machine: FlyingMachine[F, _, _], retry: Int = 3): Stream[F, Protocol] = {
    // FIXME - current implementation, the number of allocated will be less than request replicas.
    val workers = (0 to replicas).map(_ => randomSelectWorkerEndPoint).toSet
    val command = AllocationCommand(machine, workers.toSeq.map(_._1))

    Stream(Stream.emits(workers.toSeq).flatMap(worker => commandAllocation(worker, command)))
      .parJoin(workers.size)
      .handleErrorWith { t =>
        if (retry > 0) allocateToWorkers(replicas, machine, retry - 1) else Stream.raiseError(t)
      }
  }

  private def commandAllocation(worker: (TcpSocketConfig, WorkerState),
                                command: AllocationCommand[F]): Stream[F, Protocol] = {
    // Currently ignore the worker state.
    val address = worker._1
    NetService[F].writeN(address, Stream.emit(command))
  }

  private def randomSelectWorkerEndPoint: (TcpSocketConfig, WorkerState) = synchronized {
    val index = Random.nextInt(workers.size)
    workers.iterator.drop(index).next()
  }

  private def checkWorkerOutliveOrNot(): Stream[F, Unit] = {
    def infiniteChecker(): Stream[F, Unit] =
      for {
        currentTime <- Stream.eval(
          ContextShift[F].shift *> Timer[F].sleep(5000.millis) *> Timer[F].clock.realTime(MILLISECONDS)
        )
        allowedLastLived = currentTime - 5000.millis.toMillis
        _ <- Stream.eval(Sync[F].delay(synchronized {
          workers = workers.filter { case (_, state) => state.lastLive >= allowedLastLived }
        }))
        _ <- Stream.eval(Logger[F].info(s"Update workers to $workers after outlive checker."))
      } yield ()

    infiniteChecker()
  }
}