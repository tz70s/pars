package task4s.remote.serialize

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import fs2.Chunk
import task4s.remote.serialize.NativeMessage.MessageType

import fs2.Stream

/**
 * The protocol design:
 *
 * 1. The size of header.
 * 2. The size of body.
 * 3. Header options, with one word for each and collect into set.
 * 4. Header field, to be extend.
 */
object Protocol {
  // Regular sizing for alignment.
  val SizeOfHeaderPlaceHolder = 8
  val SizeOfBodyPlaceHolder = 8

  // Protocol options, to be extend.
  object Options {
    val NormalEvent: Byte = 'E'
    val TaskOffload: Byte = 'T'
    val Delimiter: Byte = 'X'
  }

  object Fields {
    val ReflectClassName: Byte = 'C'

    val StringMagicSeparator = "-F-"
    val MagicSeparator = StringMagicSeparator.getBytes(StandardCharsets.UTF_8)
    val Delimiter = "-X-".getBytes(StandardCharsets.UTF_8)
  }
}

case class Header(bodySize: Int, options: Set[Byte], fields: Map[Byte, String]) {
  import Protocol._

  def toBinary: Array[Byte] = {
    val reusableBuffer = ByteBuffer.allocate(SizeOfHeaderPlaceHolder)

    val bodySizeB = reusableBuffer.putInt(bodySize).array()
    reusableBuffer.clear()

    val optionsB = options.toArray ++ Array(Options.Delimiter)

    val fieldsB = fields
      .map {
        case (k, v) =>
          Array[Byte](k) ++ Fields.MagicSeparator ++ v.getBytes(StandardCharsets.UTF_8) ++ Fields.Delimiter
      }
      .reduce((l, r) => l ++ r)

    val headerSizeB = reusableBuffer.putInt(bodySizeB.length + optionsB.length + fieldsB.length).array()

    val buffer = headerSizeB.toBuffer ++ bodySizeB ++ optionsB ++ fieldsB
    buffer.toArray
  }
}

object Header {

  import Protocol._

  def bytesToInt(bytes: Array[Byte]): Int =
    // we only need the last five bytes.
    bytes.reverseIterator.take(5).zipWithIndex.map { case (b, i) => b >> (8 * i) }.sum

  def fromBinary(array: Array[Byte]): Header = {
    val buffer = ByteBuffer.wrap(array)

    val tmpArr = new Array[Byte](8)
    buffer.get(tmpArr)
    val headerSize = bytesToInt(tmpArr.reverse)

    val bodyArr = new Array[Byte](8)
    buffer.get(bodyArr)
    val bodySize = bytesToInt(bodyArr.reverse)

    var endOfOptions = false
    var options = Set.empty[Byte]
    while (!endOfOptions) {
      val head = buffer.get()
      if (head != Options.Delimiter) {
        options += head
      } else {
        endOfOptions = true
      }
    }

    val arr = new Array[Byte](headerSize - (SizeOfBodyPlaceHolder + options.size))
    buffer.get(arr)
    val str = new String(arr, StandardCharsets.UTF_8)
    val seq = str.split(Fields.StringMagicSeparator)

    var fields: Map[Byte, String] = Map()

    var count = 0
    while (count < seq.size) {
      fields += (seq(count).toByte -> seq(count + 1))
      count += 2
    }

    Header(bodySize, options, fields)
  }

}

case class Message(header: Header, chunk: Chunk[Byte]) {
  def toBinary: Array[Byte] = Serializer.toBinary(this)
}

case class NativeMessage(tpe: MessageType, value: AnyRef) {
  def toBinary: Array[Byte] = Serializer.toBinary(this)
}

object NativeMessage {

  sealed trait MessageType
  case object NormalEvent extends MessageType
  case object SerializableStreamEvent extends MessageType

  def fromBinary(array: Array[Byte]): NativeMessage = Serializer.fromBinary[NativeMessage](array)

  def fromStream[F[_], A](eval: Stream[F, A]) = NativeMessage(SerializableStreamEvent, SerializableStream(eval))
}
