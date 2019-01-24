package pars.cluster.internal

import pars.cluster.Coordinator
import pars.internal.remote.tcp.TcpSocketConfig

import fs2.Stream

class ZookeeperCoordinator[F[_]] extends Coordinator[F] {

  override val address: TcpSocketConfig = ???

  override def bindAndHandle(): Stream[F, Unit] = ???
}
