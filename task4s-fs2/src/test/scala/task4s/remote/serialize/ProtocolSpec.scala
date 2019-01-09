package task4s.remote.serialize

import fs2.Chunk
import org.scalatest.{Matchers, WordSpec}

class ProtocolSpec extends WordSpec with Matchers {

  "Protocol handler" should {

    "serialize header correctly" in {
      import Protocol._

      val header = Header(0, Set(Options.TaskOffload), Map(Fields.ReflectClassName -> this.getClass.getCanonicalName))
      val message = Message(header, Chunk.bytes("Hello".getBytes()))
      val binary = message.toBinary
      val expect = Serializer.fromBinary[Message](binary)

      header shouldBe expect.header
    }
  }

}
