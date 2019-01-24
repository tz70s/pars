package pars.cluster.internal

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import fs2.{Pipe, RaiseThrowable, Stream}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import pars.cluster.CoordinationProtocol._
import pars.cluster.Coordinator
import pars.internal.Protocol.Protocol
import pars.internal.remote.NetService
import pars.internal.remote.tcp.TcpSocketConfig

import scala.collection.concurrent.TrieMap
import scala.util.Random
import cats.implicits._
import pars.{NotStick, Stick}
import pars.internal.{UnsafeChannel, UnsafePars}

import scala.concurrent.duration._
import scala.util.control.NonFatal

class StandAloneCoordinator[F[_]: Concurrent: ContextShift: Timer](override val address: TcpSocketConfig)(
    implicit val acg: AsynchronousChannelGroup
) extends Coordinator[F] {

  private val server = new StandAloneCoordinatorParServer[F]

  override def bindAndHandle(): Stream[F, Unit] =
    NetService[F].bindAndHandle(address, server.logic).concurrently(server.livenessChecker())
}

object StandAloneCoordinator {
  def apply[F[_]: Concurrent: ContextShift: Timer](
      address: TcpSocketConfig
  )(implicit acg: AsynchronousChannelGroup): StandAloneCoordinator[F] =
    new StandAloneCoordinator[F](address)
}

class StandAloneCoordinatorParServer[F[_]: Concurrent: ContextShift: RaiseThrowable: Timer](
    implicit acg: AsynchronousChannelGroup
) {

  private implicit val log: Logger[F] = Slf4jLogger.unsafeCreate[F]

  @volatile private var workers = Map[TcpSocketConfig, WorkerState]()

  private val repository = TrieMap[UnsafeChannel, ParsRecords[F]]()

  def logic: Pipe[F, Protocol, Protocol] = { from =>
    from.flatMap {
      case Ping(address) => handleHealthCheck(address)
      case AllocationRequest(pars) => affinityChecker(pars.asInstanceOf[UnsafePars[F]])
      case EntryLookUpRequest(c) => handleEntryLookUp(c)
    }
  }

  private def handleHealthCheck(address: TcpSocketConfig): Stream[F, CoordinationProtocol] =
    for {
      currentTime <- Stream.eval(Timer[F].clock.realTime(MILLISECONDS))
      _ <- Stream.eval(Logger[F].info(s"Update worker $address with timestamp $currentTime millis"))
      _ <- Stream.eval(Sync[F].delay(synchronized { workers += (address -> WorkerState(address, currentTime)) }))
      pong <- Stream.emit(Pong)
    } yield pong

  private def handleEntryLookUp(channel: UnsafeChannel): Stream[F, CoordinationProtocol] =
    Stream.eval(Sync[F].delay(repository.get(channel))).flatMap { opt =>
      opt match {
        case Some(record) => Stream.emit(RequestOk(record.pars, record.records.toSeq))
        case None => Stream.emit(RequestErr(EntryLookUpException(s"No entry for $channel found.")))
      }
    }

  private def affinityChecker(pars: UnsafePars[F]): Stream[F, CoordinationProtocol] =
    pars.strategy.model match {
      case Stick(address) =>
        Stream.eval(Sync[F].delay(repository += (pars.in -> ParsRecords(pars, Set(address))))) *> NetService[F]
          .backOffWriteN(address, Stream.emit(AllocationCommand(pars, Seq(address))))
          .map {
            case CommandOk(c) => RequestOk(pars, Seq(address))
            case CommandErr(t) => RequestErr(t)
          }
      case NotStick => handleAllocationRequest(pars)
    }

  private def handleAllocationRequest(pars: UnsafePars[F]): Stream[F, CoordinationProtocol] =
    repository.get(pars.in) match {
      case Some(ext) => Stream.emit(RequestOk(pars, ext.records.toSeq))
      case None =>
        performAllocation(pars).handleErrorWith { t =>
          Stream.eval(Logger[F].error(s"Allocation error, cause : $t")) *> Stream.emit(RequestErr(t))
        }
    }

  private def performAllocation(pars: UnsafePars[F]): Stream[F, CoordinationProtocol] =
    if (workers.isEmpty)
      Stream.emit(
        RequestErr(NoAvailableWorker(s"Coordinator can't find available worker, current workers: $workers"))
      )
    else {
      val strategy = pars.strategy
      val replicas = if (strategy.replicas > workers.size) workers.size else strategy.replicas
      for {
        records <- try { allocateToWorkers(replicas, pars) } catch { case NonFatal(t) => Stream(throw t) }
        // TODO: is the repository update here correct?
        _ <- Stream.eval(Sync[F].delay(repository += (pars.in -> ParsRecords(pars, records.toSet))))
        res = RequestOk(pars, records)
      } yield res
    }

  private def allocateToWorkers(replicas: Int,
                                pars: UnsafePars[F],
                                retry: Int = 3): Stream[F, List[TcpSocketConfig]] = {
    // FIXME - current implementation, the number of allocated will be less than request replicas.
    val s = for {
      workers <- Stream.eval(Sync[F].delay((0 to replicas).map(_ => randomSelectWorkerEndPoint).toSet))
      command = AllocationCommand(pars, workers.toSeq.map(_._1))
    } yield Stream.emits(workers.toSeq).flatMap(worker => commandAllocation(worker, command))

    s.parJoin(workers.size)
      .fold(List[TcpSocketConfig]())((s, c) => c._1 :: s)
      .handleErrorWith { t =>
        if (retry > 0) allocateToWorkers(replicas, pars, retry - 1) else Stream.raiseError(t)
      }
  }

  private def commandAllocation(worker: (TcpSocketConfig, WorkerState),
                                command: AllocationCommand[F]): Stream[F, (TcpSocketConfig, WorkerState)] = {
    // Currently ignore the worker state.
    val address = worker._1
    NetService[F].writeN(address, Stream.emit(command)).flatMap {
      case CommandOk(c) =>
        if (command.pars.in == c) Stream.emit(worker)
        else Stream.raiseError(new Exception("Unexpected channel return while allocating."))
      case CommandErr(t) => Stream.raiseError(t)
    }
  }

  private def randomSelectWorkerEndPoint: (TcpSocketConfig, WorkerState) = synchronized {
    require(workers.nonEmpty)
    val index = Random.nextInt(workers.size)
    workers.iterator.drop(index).next()
  }

  def livenessChecker(ttl: FiniteDuration = 5000.millis): Stream[F, Unit] = {
    def runInfinite(): Stream[F, Unit] = {
      val l = for {
        currentTime <- Stream.eval(
          ContextShift[F].shift *> Timer[F].sleep(ttl) *> Timer[F].clock.realTime(MILLISECONDS)
        )
        allowedLastLived = currentTime - ttl.toMillis
        _ <- Stream
          .eval(Sync[F].delay(synchronized {
            val drops = workers.filter { case (_, state) => state.lastLive < allowedLastLived }
            workers = workers -- drops.keySet
            drops.keySet
          }))
          .flatMap { drop =>
            if (drop.nonEmpty)
              Stream
                .eval(Logger[F].debug(s"Rebalance occurred, current repository: $repository"))
                .concurrently(reBalance(drop))
            else Stream.eval(Logger[F].info(s"Outlive checkers update workers to $workers"))
          }
      } yield ()

      l.handleErrorWith { t: Throwable =>
        Stream.eval(Logger[F].error(s"Error occurred when running outlive checker: $t")) >> runInfinite()
      } >> runInfinite()
    }

    runInfinite()
  }

  /**
   * Commands to all visible nodes, useful for enforcing state update.
   *
   * Workflow as following:
   *
   * 1. Checkout the dropping workers and mark them for further reducing.
   * 2. Transform them and filter out channels that don't need rebalancing and sticky pars (i.e. implicit receiver)
   * 3. New allocation process (NOTE: this is a waste operation, since we re-allocate all, is not scalable.)
   * 4. Restore new one to repository.
   * 5. What about something like channel routing need info?
   *    Refer to entity look up request and resend for look up repository is fine.
   *    Therefore, we passively update channel routes.
   */
  private def reBalance(dropWorkers: Set[TcpSocketConfig]): Stream[F, Unit] =
    for {
      entries <- Stream.eval(Sync[F].delay {
        repository.map {
          case (channel, records) =>
            val renew = records.records -- dropWorkers
            if (renew.size == records.records.size) None
            else
              Some(ParsRecords(records.pars, records.records -- dropWorkers))
        }
      })
      e <- Stream
        .emits(entries.toSeq.flatten)
        .filter(p => p.pars.strategy.model == NotStick)
      r <- allocateToWorkers(e.pars.strategy.replicas, e.pars).handleErrorWith { t =>
        Stream.eval(Logger[F].error(s"Rebalancing error, see $t")) *> Stream(throw t)
      }
      _ <- Stream.eval(Sync[F].delay { repository += (e.pars.in -> ParsRecords(e.pars, r.toSet)) })
      _ <- Stream.eval(Logger[F].debug(s"Rebalance done, current repository: $repository"))
    } yield ()
}
