package task4s.task.shape

import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import org.scalatest.{Matchers, WordSpec}

class PortalBehaviorSpec extends WordSpec with Matchers {

  val TestPortalName = "test"

  "Portal Behavior" should {
    "Producing source via control message" in {
      val testkit = BehaviorTestKit(Portal.controlBehavior(TestPortalName))

    }
  }
}
