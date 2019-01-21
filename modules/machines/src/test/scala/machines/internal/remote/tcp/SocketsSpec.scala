package machines.internal.remote.tcp

import cats.effect.IO
import fs2.Chunk
import fs2.io.tcp.Socket
import org.scalatest.BeforeAndAfterAll
import fs2.Stream
import machines.NetMachinesSpec

class SocketsSpec extends NetMachinesSpec with BeforeAndAfterAll {

  "Sockets" should {

    // Temporary test, this test depends on external tcp server.
    // We should write a mock server side and assert for result.
    "work with client based operation" in {
      val message = Chunk.bytes("Hello world!\n".getBytes())

      val handler = { socket: Socket[IO] =>
        Stream.chunk(message).through(socket.writes()).drain.onFinalize(socket.endOfOutput)
      }

      val remote = TcpSocketConfig("127.0.0.1", 9977)
      val stream = SocketClientStream[IO].handle(remote, handler)

      stream.compile.drain.unsafeRunSync()
    }
  }

}
