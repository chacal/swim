package fi.jihartik.swim

import scala.concurrent.duration._

object Config {
  val probeInterval = 500.millis
  val ackTimeout = 300.millis
  val suspectPeriod = 2.seconds
  val probedMemberCount = 3
  val broadcastInterval = 200.millis
  val broadcastMemberCount = 3
  val maxBroadcastTransmitCount = 5
  val indirectProbeCount = 3
  val maxUdpMessageSize = 40000
}
