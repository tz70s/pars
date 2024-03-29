package pars.cluster.internal.curator

import cats.effect.IO
import org.apache.curator.test.TestingServer
import org.scalatest.BeforeAndAfterAll
import fs2.Stream
import pars.ParsSpec

class CuratorSpec extends ParsSpec with BeforeAndAfterAll {

  val zkServer = new TestingServer()
  val connectionString: String = zkServer.getConnectString

  override def afterAll(): Unit = zkServer.close()

  "Curator" should {
    "construct into fs2 stream" in {
      val curator = Curator[IO](connectionString)
      curator.compile.drain.unsafeRunSync()
    }

    "capable of creating znode with payload, and return the znode name" in {
      val expect = List("/a", "/b", "/c", "/d", "/e")

      val result = for {
        curator <- Curator[IO](connectionString)
        names <- Stream.emits(expect).flatMap(n => curator ++ (n, s"Hello $n!".getBytes))
      } yield names

      result.compile.toList.unsafeRunSync() shouldBe expect
    }
  }
}
