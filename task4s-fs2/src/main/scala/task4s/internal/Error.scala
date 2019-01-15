package task4s.internal

object ChannelError {
  case class ChannelOverflowException(message: String) extends Exception(message)
  case class ChannelNotExistedException(message: String) extends Exception(message)
}

object MachineError {
  case object TaskCreateRejectionT extends Throwable
  case class MachineNotFoundException(message: String) extends Exception(message)
}
