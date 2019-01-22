package pars

import cats.effect.IO
import fs2.Stream
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}

class ParsFactorySpec extends NetParsSpec {

  implicit val log: SelfAwareStructuredLogger[IO] = Slf4jLogger.unsafeCreate[IO]

  implicit val pe: ParEffect[IO] = ParEffect[IO].localAndOmitCoordinator

  "Pars Factories" should {

    "applicable for pure vararg values emission" in {
      val p = Pars(1, 2, 3)
      val s = p.evaluateToStream
      s.compile.toList.unsafeRunSync() shouldBe List(1, 2, 3)
    }

    "applicable for pure single value emission" in {
      val p = Pars.emit(1)
      val s = p.evaluateToStream
      s.compile.toList.unsafeRunSync() shouldBe List(1)
    }

    "applicable for pure sequence values emission" in {
      val expect = List(1, 2, 3, 4, 5)
      val p = Pars.emits(expect)
      val s = p.evaluateToStream
      s.compile.toList.unsafeRunSync() shouldBe expect
    }

    "applicable for side effected stream evaluation" in {
      val expect = List(1, 2, 3, 4, 5)
      val p = Pars {
        for {
          i <- Stream.emits(expect)
          _ <- Stream.eval(Logger[IO].info(s"$i"))
        } yield i
      }

      p.evaluateToStream.compile.toList.unsafeRunSync() shouldBe expect
    }
  }

  "Flying Pars" should {
    "serializable with serializer (offloading case)" in {
      val expect = List(1, 2, 3, 4, 5)

      val serializer = SerializationProvider.serializer

      val p = Pars.offload {
        for {
          i <- Stream.emits(expect)
          _ <- Stream.eval(IO { println(i) })
        } yield i
      }

      val binary = serializer.serialize(p)
      val after = binary.flatMap(b => serializer.deserialize[Pars[IO, Unit, Int]](b))

      after.toTry.get.evaluateToStream.compile.toList.unsafeRunSync() shouldBe expect
    }
  }
}
