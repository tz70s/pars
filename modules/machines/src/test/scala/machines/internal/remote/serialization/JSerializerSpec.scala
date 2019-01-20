package machines.internal.remote.serialization

import cats.implicits._
import machines.{MachinesSpec, SerializationProvider, Serializer}

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
  }
}
