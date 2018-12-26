package task4s.task.internal

import akka.stream.scaladsl.{Sink, Source}

case class ServiceKey(path: String)

sealed trait TaskReferential {
  val ref: ServiceKey
}

case class TaskSource[In, InMat](ref: ServiceKey, source: Source[In, InMat]) extends TaskReferential {
  def this(path: String, source: Source[In, InMat]) = this(ServiceKey(path), source)
}
case class TaskSink[Out, OutMat](ref: ServiceKey, sink: Sink[Out, OutMat]) extends TaskReferential {
  def this(path: String, sink: Sink[Out, OutMat]) = this(ServiceKey(path), sink)
}

case class TaskMeta[In, InMat, Out, OutMat](source: TaskSource[In, InMat], sink: TaskSink[Out, OutMat])
