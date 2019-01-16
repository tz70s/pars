package task4s.internal

case class ChannelOverflowException(message: String) extends Exception(message)

case object MachineSpawnRejectionException extends Throwable

case class MachineNotFoundException(message: String) extends Exception(message)
