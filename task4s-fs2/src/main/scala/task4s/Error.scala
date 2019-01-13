package task4s

object ChannelError {
  case class ChannelOverflowException(message: String) extends Exception(message)
  case class ChannelNotExistedException(message: String) extends Exception(message)
}

object TaskError {
  case object TaskCreateRejectionT extends Throwable
}
