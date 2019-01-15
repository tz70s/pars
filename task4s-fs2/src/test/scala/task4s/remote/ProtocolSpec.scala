package task4s.remote

import fs2.Chunk
import org.scalatest.{Matchers, WordSpec}
import task4s.Channel
import task4s.remote.serialize.SerializationProvider

class ProtocolSpec extends WordSpec with Matchers {

  "Protocol handler" should {

    "serialize header correctly" in {
      import Protocol._
      val serializer = SerializationProvider.serializer

      val header = Header(ChannelEventT.Send(Channel[Int]("TestingFakeChannel")))

      val message = Message(header, Chunk.bytes("Hello".getBytes()))
      val binary = serializer.toBinary(message)
      val expect = binary.flatMap(b => serializer.fromBinary[Message](b))

      Right(message) shouldBe expect
    }
  }

}
