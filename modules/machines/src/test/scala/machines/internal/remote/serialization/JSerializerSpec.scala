package machines.internal.remote.serialization

import cats.effect.IO
import cats.implicits._
import machines.internal.Protocol.Event
import machines.{Channel, MachinesSpec, SerializationProvider, Serializer}
import fs2.Stream

case class NormalFormCaseClazz(index: Int, value: String)

class SerializerSpec extends MachinesSpec {

  val serializer: Serializer = SerializationProvider.serializer

  "JSerializer" should {

    "serialize normal form case class" in {
      val normalForm = NormalFormCaseClazz(10, "test")

      val bs = serializer.serialize(normalForm)
      val expect = bs >>= serializer.deserialize[NormalFormCaseClazz]

      Right(normalForm) shouldBe expect
    }

    "serialize event which contains fs2 Stream" in {
      val event = Event[IO, Int](Channel("TestSerialization"), Stream(1, 2, 3))

      val bs = serializer.serialize(event)
      val expect = bs >>= serializer.deserialize[Event[IO, Int]]

      Right(event) shouldBe expect
    }
  }
}
