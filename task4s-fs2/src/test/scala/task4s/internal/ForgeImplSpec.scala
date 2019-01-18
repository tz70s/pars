package task4s.internal

import cats.effect.IO
import task4s.{Forge, Machine, Strategy, Task4sSpec}

class ForgeImplSpec extends Task4sSpec {

  "FallingMill" should {
    "milling local machine source" in {
      val expect = List(1, 2, 3)

      implicit val millImpl: Forge[IO] = ForgeImpl[IO](UnsafeFacade())

      val m = Machine.emits(expect)
      Forge.forge(m, Strategy(1)).compile.toList.unsafeRunSync() shouldBe expect
    }
  }
}
