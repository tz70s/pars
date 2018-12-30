package task4s

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

object Sample {
  def counter(x: Int): Int = x + 1
  def io(x: Int): Unit = println(x)
}

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Threads(4)
@Warmup(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 15, timeUnit = TimeUnit.SECONDS)
class SampleBenchmark {

  var count = 0

  @Benchmark
  def benchmarkCounter(): Unit =
    Sample.counter(count)

  @Benchmark
  def benchmarkIO(): Unit =
    Sample.io(count)
}
