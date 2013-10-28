package fi.jihartik.swim

import akka.actor.{ActorRef, Actor}
import scala.util.Random

class Broadcaster(controller: ActorRef, udp: ActorRef) extends Actor {
  import context.dispatcher
  var state = BroadcastState(Map())

  override def preStart = context.system.scheduler.schedule(Config.broadcastInterval, Config.broadcastInterval, controller, NeedMembersForBroadcast)

  def receive = {
    case msg: MemberMessage => state += Broadcast(msg, transmitCount = 0)
    case SendBroadcasts(members: List[Member]) => sendBroadcasts(members)
  }

  def sendBroadcasts(members: List[Member]) {
    val toBeSend = state.broadcasts
    toBeSend.foreach { bcast =>
      val targetMembers = Util.takeRandom(members, Config.broadcastMemberCount)
      sendBroadcast(targetMembers, bcast)
      state = state.updatedWithTransmit(bcast)
    }
  }

  def sendBroadcast(members: List[Member], bcast: Broadcast) {
    members.foreach(member => udp ! SendMessage(member, bcast.message))
  }
}

case class SendBroadcasts(members: List[Member])
case class Broadcast(message: MemberMessage, transmitCount: Int)

case class BroadcastState(val broadcastMap: Map[String, Broadcast]) {
  def +(broadcast: Broadcast) = this.copy(broadcastMap + (broadcast.message.member.name -> broadcast))
  def -(broadcast: Broadcast) = this.copy(broadcastMap - broadcast.message.member.name)
  def broadcasts = broadcastMap.values.toList
  def updatedWithTransmit(broadcast: Broadcast) = {
    if(broadcast.transmitCount < Config.maxBroadcastTransmitCount - 1) {  // Transmit count has not yet been updated
      this.`+`(broadcast.copy(transmitCount = broadcast.transmitCount + 1))
    } else {
      this.`-`(broadcast)
    }
  }

}