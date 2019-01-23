package pars.cluster

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import fs2.{RaiseThrowable, Stream}
import pars.internal.{ChannelRoutingTable, UnsafePars}
import pars.{Channel, Pars}
import pars.internal.remote.tcp.TcpSocketConfig

import scala.concurrent.duration._
import scala.util.Random
import pars.internal.remote.NetService
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import pars.cluster.ConnectionStateManagement.{Connect, Disconnect}
import pars.cluster.CoordinationProtocol.{Ping, Pong}
import pars.cluster.CoordinatorProxy.selectCoordinator
import pars.internal._

private[pars] class CoordinatorProxy[F[_]: RaiseThrowable: Concurrent: ContextShift: Timer](
    val coordinators: Seq[TcpSocketConfig],
    private val repository: ChannelRoutingTable[F]
)(implicit acg: AsynchronousChannelGroup) {

  import CoordinatorProxy._
  import CoordinationProtocol._

  private implicit val log: Logger[F] = Slf4jLogger.unsafeCreate[F]

  private val connectionStateManagement = new ConnectionStateManagement[F](coordinators)

  def bind: Stream[F, Unit] = connectionStateManagement.healthCheck()

  def spawn[I, O](pars: Pars[F, I, O]): Stream[F, Channel[I]] = {

    def retry(retries: Int = 3, backOff: FiniteDuration = 100.millis): Stream[F, Channel[I]] =
      NetService[F]
        .writeN(selectCoordinator(coordinators), Stream.emit(AllocationRequest(pars.toUnsafe)))
        .handleError(t => RequestErr(t))
        .flatMap {
          case RequestErr(t) =>
            if (retries > 0)
              Stream.eval(Logger[F].warn(s"Allocation failed with $t, retry")) *> Stream.eval(Timer[F].sleep(backOff)) *> retry(
                retries - 1,
                backOff * 2
              )
            else Stream.raiseError(t)
          case RequestOk(c, _) => Stream.emit(c.asInstanceOf[Channel[I]])
        }

    connectionStateManagement.current match {
      case Connect => retry()
      case Disconnect => connectionStateManagement.blockUntilConnect *> retry()
    }
  }

  def handle(protocol: CoordinatorToProxy): Stream[F, ProxyToCoordinator] =
    protocol match {
      case cmd: Command => handleCommand(cmd)
      case _ => throw new IllegalAccessError("Show not access here for other protocol subtype.")
    }

  private def handleCommand(command: Command): Stream[F, ProxyToCoordinator] =
    command match {
      case AllocationCommand(pars, workers) =>
        repository
          .allocate(pars.asInstanceOf[UnsafePars[F]], workers)
          .map(_ => CommandOk(pars.channel))

      case RemovalCommand(channel) =>
        repository.remove(channel).map(_ => CommandOk(channel))
    }
}

private[pars] object CoordinatorProxy {

  def apply[F[_]: RaiseThrowable: Concurrent: ContextShift: Timer](
      coordinators: Seq[TcpSocketConfig],
      repository: ChannelRoutingTable[F]
  )(implicit acg: AsynchronousChannelGroup): CoordinatorProxy[F] =
    new CoordinatorProxy(coordinators, repository)

  def selectCoordinator(coordinators: Seq[TcpSocketConfig]): TcpSocketConfig = {
    val index = Random.nextInt(coordinators.size)
    coordinators.iterator.drop(index).next()
  }
}

private[cluster] class ConnectionStateManagement[F[_]: Concurrent: ContextShift: Timer](
    coordinators: Seq[TcpSocketConfig]
)(
    implicit acg: AsynchronousChannelGroup
) {

  import ConnectionStateManagement._

  implicit val log: Logger[F] = Slf4jLogger.unsafeCreate[F]

  @volatile private var currentState: ConnectionState = Disconnect

  def current: ConnectionState = currentState

  def healthCheck(): Stream[F, Unit] = {
    val coordinator = selectCoordinator(coordinators)

    val pong = for {
      _ <- Stream.eval(ContextShift[F].shift *> Timer[F].sleep(1500.millis))
      pong <- NetService[F].writeN(coordinator, Stream.emit(Ping(NetService.address)))
    } yield pong

    pong.flatMap {
      case Pong =>
        for {
          _ <- Stream.eval(Logger[F].info(s"Health check to coordinator $coordinator success."))
          _ <- Stream.eval(Sync[F].delay(currentState = Connect))
          _ <- healthCheck()
        } yield ()

      case _ =>
        for {
          _ <- Stream.eval(Logger[F].info(s"Can't contact to coordinator for address: $coordinator, retry again."))
          _ <- Stream.eval(Sync[F].delay(currentState = Disconnect))
          _ <- healthCheck()
        } yield ()
    }
  }

  def blockUntilConnect: Stream[F, ConnectionState] =
    Stream.eval(ContextShift[F].shift *> Timer[F].sleep(100.millis) *> Sync[F].delay(currentState)).flatMap {
      case Connect => Stream.emit(Connect)
      case Disconnect => blockUntilConnect
    }
}

private[cluster] object ConnectionStateManagement {

  def apply[F[_]: Concurrent: ContextShift: Timer](coordinators: Seq[TcpSocketConfig])(
      implicit acg: AsynchronousChannelGroup
  ): ConnectionStateManagement[F] = new ConnectionStateManagement[F](coordinators)

  sealed trait ConnectionState
  case object Connect extends ConnectionState
  case object Disconnect extends ConnectionState
}
