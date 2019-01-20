package machines.internal

import cats.effect.IO
import machines.{Machine, MachinesSpec, ParEffect, Strategy}

class ParEffectImplSpec extends MachinesSpec {

  "FallingMill" should {
    "milling local machine source" in {
      val expect = List(1, 2, 3)

      implicit val millImpl: ParEffect[IO] = ParEffectImpl[IO](UnsafeFacade())

      val m = Machine.emits(expect)

      ParEffect[IO].spawn(m, Strategy(1)).compile.toList.unsafeRunSync() shouldBe expect
    }
  }
}
