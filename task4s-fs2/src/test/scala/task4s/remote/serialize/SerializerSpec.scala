package task4s.remote.serialize

import cats.effect.IO
import org.scalatest.{Matchers, WordSpec}
import fs2.Stream

case class NormalFormCaseClazz(index: Int, value: String)

class SerializerSpec extends WordSpec with Matchers {

  "Serializer" should {

    "serialize normal form case class" in {
      val normalForm = NormalFormCaseClazz(10, "test")

      val bs = Serializer.toBinary(normalForm)
      val expect = Serializer.fromBinary[NormalFormCaseClazz](bs)

      normalForm shouldBe expect
    }

    "serialize FS2 stream under SerializableStream wrapper" in {
      val stream = SerializableStream(Stream.eval(IO.pure(4)))

      val bs = Serializer.toBinary(stream)
      val serializedStream = Serializer.fromBinary[SerializableStream[IO, Int]](bs)

      val expect = serializedStream.ev.compile.toList.unsafeRunSync()
      expect shouldBe List(4)
    }
  }
}
