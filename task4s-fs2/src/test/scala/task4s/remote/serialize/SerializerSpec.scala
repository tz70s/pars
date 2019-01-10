package task4s.remote.serialize

import cats.effect.IO
import org.scalatest.{Matchers, WordSpec}
import fs2.Stream
import cats.implicits._

case class NormalFormCaseClazz(index: Int, value: String)

class SerializerSpec extends WordSpec with Matchers {

  val serializer = SerializationProvider.serializer

  "Serializer" should {

    "serialize normal form case class" in {
      val normalForm = NormalFormCaseClazz(10, "test")

      val bs = serializer.toBinary(normalForm)
      val expect = bs >>= serializer.fromBinary[NormalFormCaseClazz]

      Right(normalForm) shouldBe expect
    }

    "serialize FS2 stream under SerializableStream wrapper" in {
      val stream = SerializableStreamT(Stream.eval(IO.pure(4)))

      val bs = serializer.toBinary(stream)
      val serializedStream = bs >>= serializer.fromBinary[SerializableStreamT[IO, Int]]

      val expect = serializedStream.toTry.get.ev.compile.toList.unsafeRunSync()
      expect shouldBe List(4)
    }
  }
}
