package fi.jihartik.swim

import akka.actor.{Cancellable, Actor, ActorRef}
import java.util.concurrent.atomic.AtomicLong
import scala.util.Random

class FailureDetector(controller: ActorRef, udp: ActorRef) extends Actor {
  import context.dispatcher

  val probeSeqNo = new AtomicLong(0)
  var outstandingProbes = Map[Long, OutstandingProbe]()

  override def preStart = context.system.scheduler.schedule(Config.probeInterval, Config.probeInterval, controller, NeedMembersForProbing)

  def receive = {
    case ProbeMembers(members) => probeMembers(members)
    case Ping(seqNo) => sender ! Ack(seqNo)
    case Ack(seqNo) => cancelAckTimeout(seqNo)
    case AckTimedOut(seqNo) => handleAckTimeout(seqNo)
  }

  def probeMembers(members: List[Member]) {
    val probedMembers = Random.shuffle(members).take(Config.probedMemberCount)
    probedMembers.foreach(probeMember)
  }

  def probeMember(member: Member) {
    val seqNo = probeSeqNo.getAndIncrement
    val task = context.system.scheduler.scheduleOnce(Config.ackTimeout, self, AckTimedOut(seqNo))
    outstandingProbes += (seqNo -> OutstandingProbe(task, member))
    udp ! SendMessage(member, Ping(seqNo))
  }

  def cancelAckTimeout(seqNo: Long) {
    outstandingProbes.get(seqNo).foreach(probe => probe.timeoutTimer.cancel())
    outstandingProbes -= seqNo
  }

  def handleAckTimeout(seqNo: Long) {
    outstandingProbes.get(seqNo).foreach(probe => controller ! SuspectMember(probe.member))
    outstandingProbes -= seqNo
  }

  case class AckTimedOut(seqNo: Long)
  case class OutstandingProbe(timeoutTimer: Cancellable, member: Member)
}
