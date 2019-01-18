package task4s.internal.remote

import cats.effect.IO
import task4s.{Channel, SerializationProvider, Task4sSpec}
import task4s.internal.UnsafeFacade.Event.Send
import fs2.Stream

class ProtocolSpec extends Task4sSpec {

  "ProtocolParser" should {

    "convert single message to bytes" in {
      val serializer = SerializationProvider.serializer

      val parser = new ProtocolParser[IO]()

      val send = Send[IO, Int](Channel("Test"), Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

      val binaryViaParser = Stream.emit(send).through(parser.packetToBuffer).compile.toList.unsafeRunSync()

      val binaryViaSerializer = serializer.serialize(send).toTry.get.toList

      binaryViaParser shouldBe binaryViaSerializer
    }

    "reverse parsing single message" in {
      val parser = new ProtocolParser[IO]()

      val send = Send[IO, Int](Channel("Test"), Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

      val binaryViaParser = Stream.emit(send).through(parser.packetToBuffer).compile.toList.unsafeRunSync()

      val result = Stream.emits(binaryViaParser).through(parser.bufferToPacket).compile.toList.unsafeRunSync().head

      result shouldBe send
    }

    "reverse parsing concat messages" in {
      val parser = new ProtocolParser[IO]()

      val send = Send[IO, Int](Channel("Test"), Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

      val sends = (0 to 10).map(_ => send).toList

      // Convert to binary: Stream[IO, Byte]
      val binaryViaParser = Stream.emits(sends).through(parser.packetToBuffer)

      val intermediate = binaryViaParser.compile.toList.unsafeRunSync()

      val result = binaryViaParser.through(parser.bufferToPacket).compile.toList.unsafeRunSync()

      result shouldBe sends
    }
  }

}
