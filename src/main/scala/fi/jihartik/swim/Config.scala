package fi.jihartik.swim

import scala.concurrent.duration._

object Config {
  val probeInterval = 500.millis
  val ackTimeout = 300.millis
  val suspectPeriod = 2.seconds
  val probedMemberCount = 3
}
