package pars.cluster.internal.curator

import cats.effect.{Async, ContextShift, Sync}
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import fs2.Stream
import org.apache.curator.x.async.AsyncCuratorFramework
import cats.implicits._

/**
 * Minimum Curator API bindings for FS2.
 *
 * INTERNAL API.
 */
private[cluster] class Curator[F[_]: Async](private val async: AsyncCuratorFramework) {

  /**
   * Create a znode with specific path and data, return the name of created znode.
   *
   * {{{
   *
   * for {
   *   curator <- Curator("zookeeper-address")
   *   znode <- curator ++ ("/a/b" -> "hello")
   * } yield znode
   *
   * }}}
   *
   * @param path Path of created znode.
   * @param payload Payload data stored into znode.
   * @return
   */
  def ++(path: String, payload: Array[Byte]): Stream[F, String] = {
    val asyncF: F[String] = Async[F].async { cb =>
      async.create().forPath(path, payload).whenComplete { (name, throwable) =>
        if (throwable == null) cb(Right(name)) else cb(Left(throwable))
      }
    }
    Stream.eval(asyncF)
  }
}

private[cluster] object Curator {

  def apply[F[_]: ContextShift: Async](connection: String): Stream[F, Curator[F]] =
    for {
      client <- new CuratorResource(connection).start
      async = AsyncCuratorFramework.wrap(client)
      curator = new Curator[F](async)
    } yield curator
}

private[cluster] class CuratorResource[F[_]: ContextShift: Sync](connection: String) {

  import CuratorResource._

  private val retryPolicy = new ExponentialBackoffRetry(BackOffSleepTimeInit, BackOffMaxRetries)
  private val client = CuratorFrameworkFactory.newClient(connection, retryPolicy)

  private def acquire: F[CuratorFramework] =
    ContextShift[F].shift *> Sync[F].delay(client.start()) *> Sync[F].pure(client)

  private def release: F[Unit] =
    ContextShift[F].shift *> Sync[F].delay(client.close())

  def start: Stream[F, CuratorFramework] =
    Stream.bracket(acquire)(_ => release)
}

private[cluster] object CuratorResource {
  val BackOffSleepTimeInit = 1000
  val BackOffMaxRetries = 3
}
