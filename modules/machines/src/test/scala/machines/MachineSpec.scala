package machines

import cats.effect.IO
import fs2.Stream
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import machines.internal.{ParEffectImpl, UnsafeFacade}

class MachineSpec extends MachinesSpec {

  implicit val log: SelfAwareStructuredLogger[IO] = Slf4jLogger.unsafeCreate[IO]
  implicit val mill: ParEffect[IO] = ParEffectImpl(UnsafeFacade())

  "Machine Factories" should {

    "applicable for pure vararg values emission" in {
      val m = Machine(1, 2, 3)
      val s = m.evaluateToStream
      s.compile.toList.unsafeRunSync() shouldBe List(1, 2, 3)
    }

    "applicable for pure single value emission" in {
      val m = Machine.emit(1)
      val s = m.evaluateToStream
      s.compile.toList.unsafeRunSync() shouldBe List(1)
    }

    "applicable for pure sequence values emission" in {
      val expect = List(1, 2, 3, 4, 5)
      val m = Machine.emits(expect)
      val s = m.evaluateToStream
      s.compile.toList.unsafeRunSync() shouldBe expect
    }

    "applicable for side effected stream evaluation" in {
      val expect = List(1, 2, 3, 4, 5)
      val m = Machine {
        for {
          i <- Stream.emits(expect)
          _ <- Stream.eval(Logger[IO].info(s"$i"))
        } yield i
      }

      m.evaluateToStream.compile.toList.unsafeRunSync() shouldBe expect
    }
  }

  "Flying Machine" should {
    "serializable with serializer (offloading case)" in {
      val expect = List(1, 2, 3, 4, 5)

      val serializer = SerializationProvider.serializer

      val m = Machine.offload {
        for {
          i <- Stream.emits(expect)
          _ <- Stream.eval(IO { println(i) })
        } yield i
      }

      val binary = serializer.serialize(m)
      val after = binary.flatMap(b => serializer.deserialize[FlyingMachine[IO, Unit, Int]](b))

      after.toTry.get.evaluateToStream.compile.toList.unsafeRunSync() shouldBe expect
    }
  }
}
