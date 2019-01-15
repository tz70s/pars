package task4s.internal.remote.tcp

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ContextShift, IO}
import fs2.Chunk
import fs2.io.tcp.Socket
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import fs2.Stream

class SocketsSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit val acg: AsynchronousChannelGroup = AsyncChannelProvider.instance(8)
  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)

  override def afterAll(): Unit = acg.shutdown()

  "Sockets" should {

    // Temporary test, this test depends on external tcp server.
    // We should write a mock server side and assert for result.
    "work with client based operation" in {
      val message = Chunk.bytes("Hello world!\n".getBytes())

      val handler = { socket: Socket[IO] =>
        Stream.chunk(message).through(socket.writes()).drain.onFinalize(socket.endOfOutput)
      }

      val remote = TcpSocketConfig("127.0.0.1", 9977)
      val stream = SocketClientStream(remote, handler)

      stream.compile.drain.unsafeRunSync()
    }
  }

}
