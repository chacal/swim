package fi.jihartik.swim.acceptance

import fi.jihartik.swim.Config
import scala.concurrent.duration._

// More aggressive values for tests to get them pass faster
object TestConfig extends Config(
  probeInterval = 100.millis,
  ackTimeout = 100.millis,
  suspectPeriod = 500.millis,
  broadcastInterval = 100.millis
)
