package task4s.internal.remote

import org.scalatest.{Matchers, WordSpec}
import task4s.SerializationProvider

class ProtocolSpec extends WordSpec with Matchers {

  "Protocol handler" should {

    "serialize header correctly" in {
      val serializer = SerializationProvider.serializer
    }
  }

}
