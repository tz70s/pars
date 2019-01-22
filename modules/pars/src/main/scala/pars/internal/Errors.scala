package pars.internal

case class ChannelOverflowException(message: String) extends Exception(message)

case object ParsSpawnRejectionException extends Throwable

case class ParsNotFoundException(message: String) extends Exception(message)
