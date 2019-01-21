package machines.cluster

import cats.effect.IO
import fs2.Stream
import machines.cluster.CoordinationProtocol.{AllocationCommand, CommandOk, RemovalCommand}
import machines.internal.MachineRepository
import machines.{Channel, Machine, MachinesSpec, ParEffect}
import org.scalatest.Matchers

class CoordinatorProxySpec extends MachinesSpec with Matchers {

  implicit val parEffect: ParEffect[IO] = ParEffect.localAndOmitChannel()

  val channel = Channel[Int]("TestChannel")

  // FIXME - should eliminate this type annotation.
  val m = Machine.concat(channel) { s: Stream[IO, Int] =>
    for {
      i <- s
      _ <- Stream.eval(IO { println(i) })
      u <- Stream.emit(i + 1)
    } yield u
  }

  "CoordinatorProxy and Machine Repository" should {

    "work with machine creation locally based on coordinator protocol" in {

      val repository = new MachineRepository[IO]

      val proxy = CoordinatorProxy[IO](Seq.empty, repository)

      val result = for {
        response <- proxy.handle(AllocationCommand(m))
        lookUp <- Stream.eval(repository.lookUp(channel))
      } yield (response, lookUp)

      val (res, lookUp) = result.compile.toList.unsafeRunSync().head

      res shouldBe CommandOk(channel)
      lookUp shouldBe Some(m)
    }

    "work with machine deletion locally based on coordinator protocol" in {
      val repository = new MachineRepository[IO]

      val proxy = CoordinatorProxy[IO](Seq.empty, repository)

      val result = for {
        _ <- proxy.handle(AllocationCommand(m))
        exist <- Stream.eval(repository.lookUp(channel))
        _ <- proxy.handle(RemovalCommand(channel))
        notExist <- Stream.eval(repository.lookUp(channel))
      } yield (exist, notExist)

      val (exist, notExist) = result.compile.toList.unsafeRunSync().head

      exist shouldBe Some(m)
      notExist shouldBe None
    }

  }
}
