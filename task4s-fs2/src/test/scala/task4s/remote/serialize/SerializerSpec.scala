package task4s.remote.serialize

import org.scalatest.{Matchers, WordSpec}
import cats.implicits._

case class NormalFormCaseClazz(index: Int, value: String)

class SerializerSpec extends WordSpec with Matchers {

  val serializer: Serializer = SerializationProvider.serializer

  "Serializer" should {

    "serialize normal form case class" in {
      val normalForm = NormalFormCaseClazz(10, "test")

      val bs = serializer.toBinary(normalForm)
      val expect = bs >>= serializer.fromBinary[NormalFormCaseClazz]

      Right(normalForm) shouldBe expect
    }
  }
}
