package task4s.remote.serialize

import fs2.Chunk
import org.scalatest.{Matchers, WordSpec}

class ProtocolSpec extends WordSpec with Matchers {

  "Protocol handler" should {

    "serialize header correctly" in {
      import Protocol._
      val serializer = SerializationProvider.serializer

      val header = Header(0, Set(Options.TaskOffload), Map(Fields.ReflectClassName -> this.getClass.getCanonicalName))
      val message = Message(NormalEvent, Chunk.bytes("Hello".getBytes()))
      val binary = serializer.toBinary(message)
      val expect = binary.flatMap(b => serializer.fromBinary[Message](b))

      Right(message) shouldBe expect
    }
  }

}
