package task4s.internal.remote.serialization

import org.scalatest.{Matchers, WordSpec}
import cats.implicits._
import task4s.{SerializationProvider, Serializer}

case class NormalFormCaseClazz(index: Int, value: String)

class SerializerSpec extends WordSpec with Matchers {

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
