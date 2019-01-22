package pars.internal.remote

import java.util.concurrent.TimeUnit

import cats.effect.{ContextShift, IO}
import org.openjdk.jmh.annotations._
import fs2.Stream
import pars.Channel
import pars.internal.Protocol.{Event, Protocol}

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Threads(1)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
class ProtocolParserBench {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  @Param(Array("100", "1000", "10000"))
  private var numOfPackets = 0

  val parser = new ProtocolParser[IO]

  var packets: List[Protocol] = _

  var buffer: Array[Byte] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val packetStream = Stream
      .emits(0 to numOfPackets)
      .map(_ => Event[IO, Int](Channel("Test"), Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))

    packets = packetStream.compile.toList

    buffer = packetStream.through(parser.encoder).compile.toList.unsafeRunSync().toArray
  }

  @Benchmark
  def packetToBufferParsing(): Unit = {
    val buffer = Stream.emits(packets).through(parser.encoder)
    buffer.compile.drain.unsafeRunSync()
  }

  @Benchmark
  def bufferToPacketParsing(): Unit = {
    val packets = Stream.emits(buffer).through(parser.decoder)
    packets.compile.drain.unsafeRunSync()
  }

}
