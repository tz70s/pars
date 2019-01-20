package task4s.internal.remote

import java.util.concurrent.TimeUnit

import cats.effect.IO
import org.openjdk.jmh.annotations._
import fs2.Stream
import task4s.Channel
import task4s.internal.UnsafeFacade.Event.Send
import task4s.internal.UnsafeFacade.Packet

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Threads(1)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
class ProtocolParserBench {

  implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)

  @Param(Array("100", "1000", "10000"))
  private var numOfPackets = 0

  val parser = new ProtocolParser[IO]

  var packets: List[Packet] = _

  var buffer: Array[Byte] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val packetStream = Stream
      .emits(0 to numOfPackets)
      .map(_ => Send[IO, Int](Channel("Test"), Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))

    packets = packetStream.compile.toList

    buffer = packetStream.through(parser.packetToBuffer).compile.toList.unsafeRunSync().toArray
  }

  @Benchmark
  def packetToBufferParsing(): Unit = {
    val buffer = Stream.emits(packets).through(parser.packetToBuffer)
    buffer.compile.drain.unsafeRunSync()
  }

  @Benchmark
  def bufferToPacketParsing(): Unit = {
    val packets = Stream.emits(buffer).through(parser.bufferToPacket)
    packets.compile.drain.unsafeRunSync()
  }

}
